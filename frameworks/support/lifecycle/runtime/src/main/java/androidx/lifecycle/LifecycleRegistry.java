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

import static androidx.lifecycle.Lifecycle.Event.ON_CREATE;
import static androidx.lifecycle.Lifecycle.Event.ON_DESTROY;
import static androidx.lifecycle.Lifecycle.Event.ON_PAUSE;
import static androidx.lifecycle.Lifecycle.Event.ON_RESUME;
import static androidx.lifecycle.Lifecycle.Event.ON_START;
import static androidx.lifecycle.Lifecycle.Event.ON_STOP;
import static androidx.lifecycle.Lifecycle.State.CREATED;
import static androidx.lifecycle.Lifecycle.State.DESTROYED;
import static androidx.lifecycle.Lifecycle.State.INITIALIZED;
import static androidx.lifecycle.Lifecycle.State.RESUMED;
import static androidx.lifecycle.Lifecycle.State.STARTED;

import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.arch.core.internal.FastSafeIterableMap;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map.Entry;

/**
 * An implementation of {@link Lifecycle} that can handle multiple observers.
 * <p>
 * It is used by Fragments and Support Library Activities. You can also directly use it if you have
 * a custom LifecycleOwner.
 */
public class LifecycleRegistry extends Lifecycle {

    private static final String LOG_TAG = "LifecycleRegistry";

    /**
     * 保留 LifecycleObserver 并在遍历期间处理删除/添加的自定义列表。
     *
     * Invariant: at any moment of time for observer1 & observer2:
     * if addition_order(observer1) < addition_order(observer2), then
     * state(observer1) >= state(observer2),
     */
    private FastSafeIterableMap<LifecycleObserver, ObserverWithState> mObserverMap =
            new FastSafeIterableMap<>();
    /**
     * 当前LifecycleRegistry的生命周期状态。
     */
    private State mState;
    /**
     * The provider that owns this Lifecycle.
     * Only WeakReference on LifecycleOwner is kept, so if somebody leaks Lifecycle, they won't leak
     * the whole Fragment / Activity. However, to leak Lifecycle object isn't great idea neither,
     * because it keeps strong references on all other listeners, so you'll leak all of them as
     * well.
     */
    // 当前对应的生命周期提供者，使用弱引用防止内存泄漏。
    private final WeakReference<LifecycleOwner> mLifecycleOwner;
	// 记录正在初始化同步的新添加Observer的数量，大于0时，表示正在对新添加的Observer进行初始化同步
	// 防止addObserver()重入时，多次调用sync()。使sync()仅仅在最外层的addObserver()同步逻辑完成后执行一次。
    private int mAddingObserverCounter = 0;

	// 标记正在进行状态事件同步。防止moveToState()重入时，多次调用sync()。
    private boolean mHandlingEvent = false;
	// 标记发生事件嵌套。主要作用在moveToState()重入时，将mNewEventOccurred设置为true，打断正在发生的sync()同步逻辑，并重新执行。
    private boolean mNewEventOccurred = false;

    // 用于解决addObserver()过程中可能发生的remove+add，保证链表的不变式成立
    private ArrayList<State> mParentStates = new ArrayList<>();

    /**
     * Creates a new LifecycleRegistry for the given provider.
     * <p>
     * You should usually create this inside your LifecycleOwner class's constructor and hold
     * onto the same instance.
     *
     * @param provider The owner LifecycleOwner
     */
    public LifecycleRegistry(@NonNull LifecycleOwner provider) {
        mLifecycleOwner = new WeakReference<>(provider);
        mState = INITIALIZED;
    }

    /**
     * Moves the Lifecycle to the given state and dispatches necessary events to the observers.
     *
     * @param state new state
     */
    @SuppressWarnings("WeakerAccess")
    @MainThread
    public void markState(@NonNull State state) {
        moveToState(state);
    }

    /**
     * Sets the current state and notifies the observers.
     * <p>
     * Note that if the {@code currentState} is the same state as the last call to this method,
     * calling this method has no effect.
     *
     * @param event The event that was received
     */
    // 迁移到传入事件发生后对应的状态
    public void handleLifecycleEvent(@NonNull Lifecycle.Event event) {
        State next = getStateAfter(event);
        moveToState(next);
    }
	/**
	 * 将LifecycleRegistry设置到对应的传入状态next，并触发sync()进行状态事件同步。
	 */
    private void moveToState(State next) {
	    // 状态没有发生迁移时, 直接返回不进行处理
        if (mState == next) {
            return;
        }
		// 记录当前状态为传入状态
        mState = next;
		
		// 检查moveToState是否重入
	    // 检查addObserver是否重入
        if (mHandlingEvent || mAddingObserverCounter != 0) {
            mNewEventOccurred = true;
            // we will figure out what to do on upper level.
            return;
        }
		// 标记开始状态事件同步
        mHandlingEvent = true;
		// 同步新状态事件
        sync();
		// 移除状态事件同步标记
        mHandlingEvent = false;
    }

	/**
	 * 判断是否已经完成同步。
	 * 当mObserverMap首尾节点状态相同，且尾节点状态等于当前状态时，认为同步完成。
	 */
    private boolean isSynced() {
    	// 假如不存在观察者，则不需要同步，返回已同步
        if (mObserverMap.size() == 0) {
            return true;
        }
		
		// 尾节点状态和头节点状态相同，且等于当前状态，则已同步
        State eldestObserverState = mObserverMap.eldest().getValue().mState;
        State newestObserverState = mObserverMap.newest().getValue().mState;
        return eldestObserverState == newestObserverState && mState == newestObserverState;
    }
	
	/**
	 * 主要是对比当前LifecycleRegistry的状态、Observer链表的尾节点及mParentStates的最后一个元素，取出其中的最小值。
	 */
    private State calculateTargetState(LifecycleObserver observer) {
        Entry<LifecycleObserver, ObserverWithState> previous = mObserverMap.ceil(observer);

        State siblingState = previous != null ? previous.getValue().mState : null;
        State parentState = !mParentStates.isEmpty() ? mParentStates.get(mParentStates.size() - 1)
                : null;
        return min(min(mState, siblingState), parentState);
    }

	/**
	 * 通过addObserver()将Observer添加到LifecycleRegistry。
	 * Observer在添加过程中，会优先被初始化同步到一个最小状态，这个状态通过calculateTargetState()获取。
	 */
    @Override
    public void addObserver(@NonNull LifecycleObserver observer) {
	    /**
	     * 局部变量：initialState 每次 addObserver 都会初始化 
	     * 如果当前的 mState 不为 DESTROYED 那么状态就是 INITIALIZED
	     */
        State initialState = mState == DESTROYED ? DESTROYED : INITIALIZED;
		
		// 通过ObserverWithState对observer进行包装，并绑定初始的生命周期状态
        ObserverWithState statefulObserver = new ObserverWithState(observer, initialState);
		
		// 添加到集合中，如果之前已经添加过了，putIfAbsent() 方法会直接返回之前添加过的 ObserverWithState
		// 如果是第一次添加 那么会添加成功 并返回null
        ObserverWithState previous = mObserverMap.putIfAbsent(observer, statefulObserver);
		
		// 重复添加直接return
        if (previous != null) {
            return;
        }
		
		// 假如lifecycleOwner已销毁，不进行后续操作
        LifecycleOwner lifecycleOwner = mLifecycleOwner.get();
        if (lifecycleOwner == null) {
            // it is null we should be destroyed. Fallback quickly
            return;
        }
		
		// 判断是否重入，重入有2个指标
		// mHandlingEvent表示正在执行生命周期迁移导致的sync()同步
		// mAddingObserverCounter>0 表示addObserver()导致的单个Observer同步
        boolean isReentrance = mAddingObserverCounter != 0 || mHandlingEvent;


		// 通过calculateTargetState获取当前目标需要迁移的目标状态
        State targetState = calculateTargetState(observer);
		
		// 正在添加Observer的记录自增
        mAddingObserverCounter++;

		// 使Observer迁移到目标状态
	    //
	    // 当Observer的状态小于目标状态时，升级到目标状态
	    // Observer的初始状态时DESTROYED或INITIALIZED，且当初始状态为DESTROYED时，目标状态
	    // 也应为DESTROYED，所以新添加的Observer在初始化同步的时候只需要考虑升级同步。
	    // 
	    // 这里同时做了mObserverMap.contains(observer)的判断，之所以要这么处理，是因为有时候
	    // 用户会在observer的生命周期回调中removeObserver移除自身，当发生这种情况时，立即结束
	    // 迁移操作
        while ((statefulObserver.mState.compareTo(targetState) < 0
                && mObserverMap.contains(observer))) {
            // 缓存observer的状态，用于remove+add问题
            pushParentState(statefulObserver.mState);
			// 派分事件
            statefulObserver.dispatchEvent(lifecycleOwner, upEvent(statefulObserver.mState));
			// 移除状态缓存
            popParentState();
			
			// 由于可能存在的变更，重新调用calculateTargetState获取当前目标需要迁移的目标状态
            targetState = calculateTargetState(observer);
        }

		// 非重入状态执行sync同步
        if (!isReentrance) {
            // we do sync only on the top level.
            sync();
        }
		// 正在添加Observer的记录自减
        mAddingObserverCounter--;
    }

    private void popParentState() {
        mParentStates.remove(mParentStates.size() - 1);
    }

    private void pushParentState(State state) {
        mParentStates.add(state);
    }

    @Override
    public void removeObserver(@NonNull LifecycleObserver observer) {
        // we consciously decided not to send destruction events here in opposition to addObserver.
        // Our reasons for that:
        // 1. These events haven't yet happened at all. In contrast to events in addObservers, that
        // actually occurred but earlier.
        // 2. There are cases when removeObserver happens as a consequence of some kind of fatal
        // event. If removeObserver method sends destruction events, then a clean up routine becomes
        // more cumbersome. More specific example of that is: your LifecycleObserver listens for
        // a web connection, in the usual routine in OnStop method you report to a server that a
        // session has just ended and you close the connection. Now let's assume now that you
        // lost an internet and as a result you removed this observer. If you get destruction
        // events in removeObserver, you should have a special case in your onStop method that
        // checks if your web connection died and you shouldn't try to report anything to a server.
        mObserverMap.remove(observer);
    }

    /**
     * The number of observers.
     *
     * @return The number of observers.
     */
    @SuppressWarnings("WeakerAccess")
    public int getObserverCount() {
        return mObserverMap.size();
    }

    @NonNull
    @Override
    public State getCurrentState() {
        return mState;
    }
	
	/**
	 * 计算事件发生后, 将会迁移到的状态
	 */
    static State getStateAfter(Event event) {
        switch (event) {
            case ON_CREATE:
            case ON_STOP:
                return CREATED;
            case ON_START:
            case ON_PAUSE:
                return STARTED;
            case ON_RESUME:
                return RESUMED;
            case ON_DESTROY:
                return DESTROYED;
            case ON_ANY:
                break;
        }
        throw new IllegalArgumentException("Unexpected event value " + event);
    }
	
	/**
	 * 计算传入状态降级所对应的事件
	 */
    private static Event downEvent(State state) {
        switch (state) {
            case INITIALIZED:
                throw new IllegalArgumentException();
            case CREATED:
                return ON_DESTROY;
            case STARTED:
                return ON_STOP;
            case RESUMED:
                return ON_PAUSE;
            case DESTROYED:
                throw new IllegalArgumentException();
        }
        throw new IllegalArgumentException("Unexpected state value " + state);
    }
	
	/**
	 * 计算传入状态升级所对应的事件
	 */
    private static Event upEvent(State state) {
        switch (state) {
            case INITIALIZED:
            case DESTROYED:
                return ON_CREATE;
            case CREATED:
                return ON_START;
            case STARTED:
                return ON_RESUME;
            case RESUMED:
                throw new IllegalArgumentException();
        }
        throw new IllegalArgumentException("Unexpected state value " + state);
    }
	/**
	 * forwardPass(LifecycleOwner lifecycleOwner)和backwardPass()类似，
	 * 只是迭代器正向遍历，链表头部优先升级，以确保不变式成立。
	 */
    private void forwardPass(LifecycleOwner lifecycleOwner) {
        Iterator<Entry<LifecycleObserver, ObserverWithState>> ascendingIterator =
                mObserverMap.iteratorWithAdditions();
        while (ascendingIterator.hasNext() && !mNewEventOccurred) {
            Entry<LifecycleObserver, ObserverWithState> entry = ascendingIterator.next();
            ObserverWithState observer = entry.getValue();
            while ((observer.mState.compareTo(mState) < 0 && !mNewEventOccurred
                    && mObserverMap.contains(entry.getKey()))) {
                pushParentState(observer.mState);
                observer.dispatchEvent(lifecycleOwner, upEvent(observer.mState));
                popParentState();
            }
        }
    }
	/**
	 * 降级同步，同步过程优先进行降级同步。
	 * 为确保不变式，mObserverMap的遍历从尾部指向头部，优
	 * 先减少尾节点的状态值，通过descendingIterator()构建逆向的迭代器实现。
	 */
    private void backwardPass(LifecycleOwner lifecycleOwner) {
    	// 逆向迭代器
        Iterator<Entry<LifecycleObserver, ObserverWithState>> descendingIterator =
                mObserverMap.descendingIterator();
		
		// 通过迭代器遍历，遇到新事件标识时中断
        while (descendingIterator.hasNext() && !mNewEventOccurred) {
            Entry<LifecycleObserver, ObserverWithState> entry = descendingIterator.next();
            ObserverWithState observer = entry.getValue();
		
			// 循环对比单个观察者的状态，直到单个观察者同步到目标状态
            while ((observer.mState.compareTo(mState) > 0 && !mNewEventOccurred
                    && mObserverMap.contains(entry.getKey()))) {
                    
                // 获取状态对应的事件
                Event event = downEvent(observer.mState);
				// 缓存observer的状态，用于remove+add问题
                pushParentState(getStateAfter(event));
				// 派分事件
                observer.dispatchEvent(lifecycleOwner, event);
				// 移除observer状态缓存
                popParentState();
            }
        }
    }

    /**
     * 同步新状态到所有保存的Observer。
	 * while循环通过isSynced()判断是否完成同步，
	 * 尝试使用backwardPass()和backwardPass()进行同步操作直到同步完成，
	 * 前者处理前台到销毁的过程，后者则相反。
	 */
    private void sync() {
    	// 同步时，假如生命周期组件已回收，抛出异常
        LifecycleOwner lifecycleOwner = mLifecycleOwner.get();
        if (lifecycleOwner == null) {
            Log.w(LOG_TAG, "LifecycleOwner is garbage collected, you shouldn't try dispatch "
                    + "new events from it.");
            return;
        }
		
		// 循环，直到isSynced判断同步完成
        while (!isSynced()){
			
			// 移除新事件标记
            mNewEventOccurred = false;

			// 头节点状态大于当前状态，降级同步
	        // 例：RESUMED->STARTED
    	    // 不用对头结点做非空判断，因为头结点不存在时，isSynced返回true
            if (mState.compareTo(mObserverMap.eldest().getValue().mState) < 0) {
                backwardPass(lifecycleOwner);
            }

			// 尾节点状态小于当前状态，升级同步
	        // 例：STARTED->RESUMED
	        // 需要检测mNewEventOccurred，降级同步时有可能发生事件嵌套
	        // 需要对尾节点作出空判断，降级同步时有可能将节点移除，
            Entry<LifecycleObserver, ObserverWithState> newest = mObserverMap.newest();
            if (!mNewEventOccurred && newest != null
                    && mState.compareTo(newest.getValue().mState) > 0) {
                forwardPass(lifecycleOwner);
            }
        }
		// 移除新事件标记
        mNewEventOccurred = false;
    }
	/**
	 * 获取两个状态中的较小值
	 */
    static State min(@NonNull State state1, @Nullable State state2) {
        return state2 != null && state2.compareTo(state1) < 0 ? state2 : state1;
    }

    static class ObserverWithState {
    	// 当前状态
        State mState;
		// observer封装
        GenericLifecycleObserver mLifecycleObserver;

		// 构造方法传入 实现 LifecycleObserver 的观察者，和初始化状态
        ObserverWithState(LifecycleObserver observer, State initialState) {
        	// Lifecycling对observer进行适配(泛型反射、GenericLifecycleObserver、FullLifecycleObserver)
            mLifecycleObserver = Lifecycling.getCallback(observer);
            mState = initialState;
        }
		// 封装的事件派分逻辑
        void dispatchEvent(LifecycleOwner owner, Event event) {
            State newState = getStateAfter(event);
            mState = min(mState, newState);
            mLifecycleObserver.onStateChanged(owner, event);
            mState = newState;
        }
    }
}
