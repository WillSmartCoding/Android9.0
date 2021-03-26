/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.lifecycle;

import static androidx.lifecycle.Lifecycle.State.DESTROYED;
import static androidx.lifecycle.Lifecycle.State.STARTED;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.arch.core.internal.SafeIterableMap;
import androidx.arch.core.executor.ArchTaskExecutor;

import java.util.Iterator;
import java.util.Map;

/**
 * LiveData is a data holder class that can be observed within a given lifecycle.
 * This means that an {@link Observer} can be added in a pair with a {@link LifecycleOwner}, and
 * this observer will be notified about modifications of the wrapped data only if the paired
 * LifecycleOwner is in active state. LifecycleOwner is considered as active, if its state is
 * {@link Lifecycle.State#STARTED} or {@link Lifecycle.State#RESUMED}. An observer added via
 * {@link #observeForever(Observer)} is considered as always active and thus will be always notified
 * about modifications. For those observers, you should manually call
 * {@link #removeObserver(Observer)}.
 *
 * <p> An observer added with a Lifecycle will be automatically removed if the corresponding
 * Lifecycle moves to {@link Lifecycle.State#DESTROYED} state. This is especially useful for
 * activities and fragments where they can safely observe LiveData and not worry about leaks:
 * they will be instantly unsubscribed when they are destroyed.
 *
 * <p>
 * In addition, LiveData has {@link LiveData#onActive()} and {@link LiveData#onInactive()} methods
 * to get notified when number of active {@link Observer}s change between 0 and 1.
 * This allows LiveData to release any heavy resources when it does not have any Observers that
 * are actively observing.
 * <p>
 * This class is designed to hold individual data fields of {@link ViewModel},
 * but can also be used for sharing data between different modules in your application
 * in a decoupled fashion.
 *
 * @param <T> The type of data held by this instance
 * @see ViewModel
 */
public abstract class LiveData<T> {
    private final Object mDataLock = new Object();
	// mVersion 初始值
    static final int START_VERSION = -1;
	// mData 默认值
    private static final Object NOT_SET = new Object();
	// LiveData的观察者列表，可以看到实际对象是ObserverWrapper类型，即所有传入的observer都会被ObserverWrapper及其子类封装。
    private SafeIterableMap<Observer<? super T>, ObserverWrapper> mObservers =
            new SafeIterableMap<>();

    // 当前可见的活跃的观察者个数
    private int mActiveCount = 0;
	// 实际数据,类型为 Object 而非 T
    private volatile Object mData = NOT_SET;
    // when setData is called, we set the pending data and actual data swap happens on the main
    // thread
    private volatile Object mPendingData = NOT_SET;
    private int mVersion = START_VERSION;


    private final Runnable mPostValueRunnable = new Runnable() {
        @Override
        public void run() {
            Object newValue;
            synchronized (mDataLock) {
                newValue = mPendingData;
                mPendingData = NOT_SET;
            }
            //noinspection unchecked
            setValue((T) newValue);
        }
    };

	public LiveData(T value) {
        mData = value;
		// mVersion = 0;
        mVersion = START_VERSION + 1;
    }

    /**
     * Creates a LiveData with no value assigned to it.
     */
    public LiveData() {
        mData = NOT_SET;
		// mVersion = -1;
        mVersion = START_VERSION;
    }
	/**
	 * 判断是否要将数据分发到指定的 ObserverWrapper
	 */
    private void considerNotify(ObserverWrapper observer) {
	    // 是否可以分发数据到指定的 observer, 由 mActive 来控制
    	// mActive值跟 宿主的 Lifecycle声明周期有关 如果状态为 States.Start 或者 States.Resume 那么这个值就为true ，否则为false
    	// 以Activity为例，只有在onStart onResume onPause 才能接受到事件（onStart之后 onStop之前）
        if (!observer.mActive) {
            return;
        }

        // 重复检查观察者是否应该处于激活状态，检查失败则通知观察者取消激活。
        // 主要是做容错：试想下如果用户开启了一个子线程发送消息，Activity 关闭了可能状态还没来得及改变，所以要做二次状态确认
        if (!observer.shouldBeActive()) {
			// active -> inactive 时通知更新状态
	        // inactive 状态下就不需要分发数据了
            observer.activeStateChanged(false);
            return;
        }
		/**
		 * 注意能都走到这了  代表 Owner 是可见状态
		 */
		
		// 1.若 ObserverWrapper 持有的数据值已是最新版本, 自然也不用分发
		// 2.例如：第一次进来，mLastVersion = -1，但是 mVersion在 setValue中进行+1，所以这个if是进不来的
        if (observer.mLastVersion >= mVersion) {
            return;
        }
		/**
		 * 注意：只有宿主可见的 Owner 的 Observer 才能统一version 并且在 onChanged 回调中拿到数据
		 */
		// 统一 mLastVersion 和 mVersion 
        observer.mLastVersion = mVersion;
        // 通知 observer 数据有更新
    	// observer.mObserver 是调用方实际传入的
        observer.mObserver.onChanged((T) mData);
    }

	// 看到这两个标记值 只在本类中使用，一般能想到的就是多线程的操作，另一种就是递归调用（因为LiveData只支持主线程操作）
	// 用于标记是否真正派发数据
    private boolean mDispatchingValue;
	// 用于标记当前派发是否失效。
    @SuppressWarnings("FieldCanBeLocal")
    private boolean mDispatchInvalidated;

	/**
	 * @param initiator
	 * 若参数 initiator 非空,则表示只通知特定的 ObserverWrapper, 否则是回调通知所有 ObserverWrapper
	 * 由于只在主线程中调用,因此不用做多线程处理
	 */ 
    private void dispatchingValue(@Nullable ObserverWrapper initiator) {
    	// 小技巧: 在遍历通知各 ObserverWrapper 期间, 若数据发生了变化, 则会重新遍历通知
        if (mDispatchingValue) {
            mDispatchInvalidated = true;
            return;
        }
        mDispatchingValue = true;
        do {
            mDispatchInvalidated = false;
            if (initiator != null) {// initiator 非空时,只更新特定 ObserverWrapper
                considerNotify(initiator);// 实际更新数据的方法
                initiator = null;
            } else {// 否则遍历更新所有 ObserverWrapper
            	// SafeIterableMap<Observer<? super T>, ObserverWrapper> mObservers 是存储所有Observer的集合
                for (Iterator<Map.Entry<Observer<? super T>,ObserverWrapper>> iterator = mObservers.iteratorWithAdditions(); 
						iterator.hasNext(); ) {

						
						// iterator.next().getValue()取出 ObserverWrapper 传给 considerNotify（）方法，分发
                    	considerNotify(iterator.next().getValue());
						// 若数据发生变化, 则退出for循环
    	            	// 而外层 do...while() 判定条件为true,就会重新遍历通知各 ObserverWrapper;
        	            if (mDispatchInvalidated) {
            	            break;
                	    }
                }
            }
        } while (mDispatchInvalidated);
        mDispatchingValue = false;
    }

    /**
     * Adds the given observer to the observers list within the lifespan of the given
     * owner. The events are dispatched on the main thread. If LiveData already has data
     * set, it will be delivered to the observer.
     * <p>
     * The observer will only receive events if the owner is in {@link Lifecycle.State#STARTED}
     * or {@link Lifecycle.State#RESUMED} state (active).
     * <p>
     * If the owner moves to the {@link Lifecycle.State#DESTROYED} state, the observer will
     * automatically be removed.
     * <p>
     * When data changes while the {@code owner} is not active, it will not receive any updates.
     * If it becomes active again, it will receive the last available data automatically.
     * <p>
     * LiveData keeps a strong reference to the observer and the owner as long as the
     * given LifecycleOwner is not destroyed. When it is destroyed, LiveData removes references to
     * the observer &amp; the owner.
     * <p>
     * If the given owner is already in {@link Lifecycle.State#DESTROYED} state, LiveData
     * ignores the call.
     * <p>
     * If the given owner, observer tuple is already in the list, the call is ignored.
     * If the observer is already in the list with another owner, LiveData throws an
     * {@link IllegalArgumentException}.
     *
     * @param owner    The LifecycleOwner which controls the observer
     * @param observer The observer that will receive the events
     */
     
	/**
	 * owner 这里我们用Activity来理解
 	 * observer 观察者
	 */
    @MainThread
    public void observe(@NonNull LifecycleOwner owner, @NonNull Observer<? super T> observer) {
        assertMainThread("observe");
		// 若 LifecycleOwner 已处于已被销毁,则忽略该 observer
        if (owner.getLifecycle().getCurrentState() == DESTROYED) {
            // ignore（什么都不做）
            return;
        }
		// 将 LifecycleOwner 和 Observer 功能进行绑定包装
        // 生成支持生命周期感知的 Observer: LifecycleBoundObserver
        LifecycleBoundObserver wrapper = new LifecycleBoundObserver(owner, observer);
		// 避免重复添加相同的observer，如果存在直接返回(观察者不允许绑定多个生命周期，否则抛出异常。)
		// 如果 observer 存在，则不会覆盖之前的 value 也就是 wrapper，并且返回之前的 wrapper
		// 如果 observer 不存在，就添加key和value，返回null
        ObserverWrapper existing = mObservers.putIfAbsent(observer, wrapper);
		// 如果existing 不为null 代表之前添加过，isAttachedTo判断传入的Owner是否是同一个生命周期宿主，否则会抛异常
		// 在 isAttachedTo 中 mOwner == owner; mOwner 就是第一次添加时 通过构造行数传入的 宿主
        if (existing != null && !existing.isAttachedTo(owner)) {
            throw new IllegalArgumentException("Cannot add the same observer"
                    + " with different lifecycles");
        }
        if (existing != null) {
            return;
        }
		// 实现对 LifecycleOwner 生命周期的感知
        // 关键还是 LifecycleBoundObserver 类,我们马上进去看一下
        owner.getLifecycle().addObserver(wrapper);
    }

    /**
     * Adds the given observer to the observers list. This call is similar to
     * {@link LiveData#observe(LifecycleOwner, Observer)} with a LifecycleOwner, which
     * is always active. This means that the given observer will receive all events and will never
     * be automatically removed. You should manually call {@link #removeObserver(Observer)} to stop
     * observing this LiveData.
     * While LiveData has one of such observers, it will be considered
     * as active.
     * <p>
     * If the observer was already added with an owner to this LiveData, LiveData throws an
     * {@link IllegalArgumentException}.
     *
     * @param observer The observer that will receive the events
     */
    /**
     * 添加忽略生命周期侦测特性的观察者。通过该方法加入的观察者不需要传入生命周期参数，
     * 即忽略生命周期侦测。所以使用该方法时需要注意，在合适的时候调用removeObserver()移除观察，避免发生内存泄漏。
     *
     * 通过observeForever()传入的观察者，将会被包装为AlwaysActiveObserver，
     * 该包装类的shouldBeActive()方法估计返回true，即任何数据更新都会通知到该观察者。
     * 同一个LiveData中，观察者不能同时 持有 / 忽略 生命周期特性，两者互斥。新加入的观察者，
     * 在方法的最后都会通过activeStateChanged(true)通知LiveData更新数据到观察者。
     */
    @MainThread
    public void observeForever(@NonNull Observer<? super T> observer) {
        assertMainThread("observeForever");
        AlwaysActiveObserver wrapper = new AlwaysActiveObserver(observer);
        ObserverWrapper existing = mObservers.putIfAbsent(observer, wrapper);
        if (existing != null && existing instanceof LiveData.LifecycleBoundObserver) {
            throw new IllegalArgumentException("Cannot add the same observer"
                    + " with different lifecycles");
        }
        if (existing != null) {
            return;
        }
		// 这句代码三件事：
		// 1.状态为可见
		// 2.分发数据
		// 3.mActiveCount+1
        wrapper.activeStateChanged(true);
    }

    /**
     * Removes the given observer from the observers list.
     *
     * @param observer The Observer to receive events.
     */
    @MainThread
    public void removeObserver(@NonNull final Observer<? super T> observer) {
        assertMainThread("removeObserver");
        ObserverWrapper removed = mObservers.remove(observer);
        if (removed == null) {
            return;
        }
        removed.detachObserver();
		// mActiveCount - 1
        removed.activeStateChanged(false);
    }

    /**
     * Removes all observers that are tied to the given {@link LifecycleOwner}.
     *
     * @param owner The {@code LifecycleOwner} scope for the observers to be removed.
     */
    @SuppressWarnings("WeakerAccess")
    @MainThread
    public void removeObservers(@NonNull final LifecycleOwner owner) {
        assertMainThread("removeObservers");
        for (Map.Entry<Observer<? super T>, ObserverWrapper> entry : mObservers) {
            if (entry.getValue().isAttachedTo(owner)) {
                removeObserver(entry.getKey());
            }
        }
    }

    /**
     * 在任意线程中调用，传入的数据会暂存为mPendingData，
     * 最终会通过在主线程中调用setValue(mPendingData)进行数据更新。
     * 
     * 注意的是，postValue()被多次调用时，暂存数据mPendingData会被postValue()传入的数据覆盖，
     * 最终数据为最后一次的数据。而postValue()发起的主线程任务，在执行到前，只会存在一个任务。
     * 多次postValue()调用，在真正主线程执行前，共享同一次任务。
     * 而其中的暂存数据mPendingData会被覆盖为最后一次postValue()的传入值。
     * 
     * 使用postValue()应该注意该问题，极短时间内多次postValue()设置数据，会可能导致数据更新丢失。
     * 
     */
    protected void postValue(T value) {
        boolean postTask;
        synchronized (mDataLock) {
            postTask = mPendingData == NOT_SET;
            mPendingData = value;
        }
        if (!postTask) {
            return;
        }
        ArchTaskExecutor.getInstance().postToMainThread(mPostValueRunnable);
    }

    /**
     * 只能在主线程执行，非主线程调用抛出异常。mVersion+1，mData设置为传入数据，
     * 并通过dispatchingValue(null)通知所有观察者数据数据更新。
	 * 注意数据设置时，不会进行任何的对比和校验。即设置相同数据时，同样视为更新数据。
     */
    @MainThread
    protected void setValue(T value) {
    	// 检测是否在主线程，如果在非主线程会抛出异常
        assertMainThread("setValue");
		// 消息的 version += 1 
        mVersion++;
		// 将数据先保存下俩
        mData = value;
		// 分发一个null
        dispatchingValue(null);
    }

    /**
     * Returns the current value.
     * Note that calling this method on a background thread does not guarantee that the latest
     * value set will be received.
     *
     * @return the current value
     */
    @Nullable
    public T getValue() {
        Object data = mData;
        if (data != NOT_SET) {
            //noinspection unchecked
            return (T) data;
        }
        return null;
    }

    int getVersion() {
        return mVersion;
    }

    /**
     * Called when the number of active observers change to 1 from 0.
     * <p>
     * This callback can be used to know that this LiveData is being used thus should be kept
     * up to date.
     */
    protected void onActive() {

    }

    /**
     * Called when the number of active observers change from 1 to 0.
     * <p>
     * This does not mean that there are no observers left, there may still be observers but their
     * lifecycle states aren't {@link Lifecycle.State#STARTED} or {@link Lifecycle.State#RESUMED}
     * (like an Activity in the back stack).
     * <p>
     * You can check if there are observers via {@link #hasObservers()}.
     */
    protected void onInactive() {

    }

    /**
     * Returns true if this LiveData has observers.
     *
     * @return true if this LiveData has observers
     */
    @SuppressWarnings("WeakerAccess")
    public boolean hasObservers() {
        return mObservers.size() > 0;
    }

    /**
     * Returns true if this LiveData has active observers.
     *
     * @return true if this LiveData has active observers
     */
    @SuppressWarnings("WeakerAccess")
    public boolean hasActiveObservers() {
        return mActiveCount > 0;
    }

	/**
	 * 核心功能类，ObserverWrapper的一个子类。通过LiveData.observe()传入的观察者将会被此类包装。
	 * 该类是LiveData生命周期监管的核心，该类实现了LifecycleEventObserver接口，
	 * 并在创建后注册到观察者对应的生命周期内，以实现激活状态管理和主动释放。
	 */
    class LifecycleBoundObserver extends ObserverWrapper implements GenericLifecycleObserver {
        @NonNull final LifecycleOwner mOwner;

        LifecycleBoundObserver(@NonNull LifecycleOwner owner, Observer<? super T> observer) {
            super(observer);
            mOwner = owner;
        }
		/**
		 * 当处于生命周期处于Start之后 - Stop之前时，观察者激活。
		 */
        @Override
        boolean shouldBeActive() {
        	// 此处定义了哪些生命周期是 `active` 的
	        // 结合 Lifecycle.State 类 ,我们知道是: STARTED/RESUMED 两种状态
    	    // 对应于生命周期 `onStart()` 之后到 `onStop()` 之前
            return mOwner.getLifecycle().getCurrentState().isAtLeast(STARTED);
        }

		// 生命周期变化时回调本方法
        @Override
        public void onStateChanged(LifecycleOwner source, Lifecycle.Event event) {
			// 进行自我状态校验和处理。当处于Destory时，
			// 通知LiveData移除观察者，LiveData会把包装类从mObservers列表中移除，且触发其detachObserver()方法。
			// 避免，内存泄漏
            if (mOwner.getLifecycle().getCurrentState() == DESTROYED) {
                removeObserver(mObserver);
                return;
            }
			// 当前生命周期宿主状态发生变化
            activeStateChanged(shouldBeActive());
        }
		/**
		 * 直接判断生命周期对象是否相同(用于判断是否绑定了不同的生明周期宿主)
		 * 不允许绑定不相同的宿主，因为不同的宿主声明周期状态不同，会有冲突
		 */
        @Override
        boolean isAttachedTo(LifecycleOwner owner) {
            return mOwner == owner;
        }

		/**
		 * 将自身从生命周期中移除，完成整个解绑过程。
		 */
        @Override
        void detachObserver() {
            mOwner.getLifecycle().removeObserver(this);
        }
    }
	/**
	 * LiveData 的内部抽象类
	 * 包装用户传入的 Observer, 提供数据版本记录以及active状态(生命周期)判断
	 */
    private abstract class ObserverWrapper {
    	// 在构造方法中传入的观察者实体，即用户构建的观察者。
        final Observer<? super T> mObserver;
		// 当前观察者的激活状态，非激活状态时将不会触发观察者的回调。
        boolean mActive;
        // 当前持有的数据版本号,初始值为 -1
        int mLastVersion = START_VERSION;

        ObserverWrapper(Observer<? super T> observer) {
            mObserver = observer;
        }
		/**
		 * 判断观察者是否绑定了生命周期，由子类实现，默认为false。
		 * true 时才会触发 activeStateChanged()
		 */
        abstract boolean shouldBeActive();

		/**
		 * 判断观察者是否绑定了生命周期，由子类实现，默认为false。
		 */
        boolean isAttachedTo(LifecycleOwner owner) {
            return false;
        }
		/**
		 * 观察者从LiveData移除时，LiveData会调用观察者包装对象的该方法，以处理必要的释放操作。
		 */
        void detachObserver() {
        }
		
		/**
		 * 更新本 ObserverWrapper或其子类的状态 的状态
		 * 观察者的激活状态通过该方法作出改变。
		 */
        void activeStateChanged(boolean newActive) {
        	// 若状态没变化,不需要更新,直接返回
            if (newActive == mActive) {
                return;
            }
			// 新状态赋值给旧状态
            mActive = newActive;
			// 原先是否是活跃状态，取决于LiveData的mActiveCount
            boolean wasInactive = LiveData.this.mActiveCount == 0;
			// 根据新状态，修改LiveData的mActiveCount
            LiveData.this.mActiveCount += mActive ? 1 : -1;
			// 处于 active 状态的 observer 数量从0 -> 1时,触发 onActive() 方法
			// 如果原先是非活跃，而新的状态是活跃
            if (wasInactive && mActive) {
                onActive();
            }
			// 处于 active 状态的 observer 数量从 1 -> 0时,触发 onInactive() 方法
	        // 提供给观察者释放资源的机会
	        // 如果原先是活跃，而新的状态是非活跃
            if (LiveData.this.mActiveCount == 0 && !mActive) {
                onInactive();
            }
			// observer 从  inactive -> active 时, 更新数据
            if (mActive) {
				// 将数据发送给该 observer
                dispatchingValue(this);
            }
        }
    }

	/**
	 * ObserverWrapper的一个子类。通过LiveData.observeForever()传入的观察者将会被此类包装。
	 * 被该类包装的观察者不关注生命周期，不受生命周期影响，并且激活状态永久为true。
	 * 使用该方法时应该注意手动释放观察者，以避免内存泄漏。
	 */
    private class AlwaysActiveObserver extends ObserverWrapper {

        AlwaysActiveObserver(Observer<? super T> observer) {
            super(observer);
        }

        @Override
        boolean shouldBeActive() {
            return true;
        }
    }
	/**
	 * 该处将会检测是否在主线程调用，否则抛出异常。
	 */
    private static void assertMainThread(String methodName) {
        if (!ArchTaskExecutor.getInstance().isMainThread()) {
            throw new IllegalStateException("Cannot invoke " + methodName + " on a background"
                    + " thread");
        }
    }
}
