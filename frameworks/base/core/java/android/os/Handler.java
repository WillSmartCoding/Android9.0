/*
 * Copyright (C) 2006 The Android Open Source Project
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

package android.os;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.util.Log;
import android.util.Printer;

import java.lang.reflect.Modifier;

/**
 * Handler 允许您发送和处理{@link Message}以及与线程的{@link MessageQueue}关联的可运行对象。
 * 每个处理程序实例都与一个线程和该线程的消息队列相关联。当您创建一个新的处理程序时，
 * 它被绑定到正在创建它的线程的线程/消息队列——从那时起，它将消息和runnable传递到该消息队列，
 * 并在它们从消息队列中出来时执行它们。
 * 
 * <p>
 * Handler 有两个主要用途：
 * （1）将消息和runnable安排为将来某个时间点执行；
 * （2）将要在不同线程上执行的操作排队。
 * 
 * <p>
 * 调度消息是通过
 * {@link # post},
 * {@link # posttime(Runnable，long)},
 * {@link # postDelayed},
 * {@link # sendEmptyMessage},
 * {@link # sendMessage},
 * {@link # sendMessageAtTime}和
 * {@link # sendMessageDelayed}方法完成的。
 * 
 * <em>post</em> 版本允许您将可运行对象排队，以便在接收到它们时由消息队列调用；
 * <em>sendMessage</em> 版本允许您将{@link Message}对象排队，该对象包含一组数据，
 * 这些数据将由处理程序的{@link#handleMessage}方法处理（需要实现处理程序的子类）。
 * 
 * <p>
 * 在向 Handler 投递或发送邮件时，您可以在消息队列准备就绪后立即允许处理该项，
 * 也可以指定处理该项之前的延迟或处理该项的绝对时间。
 * 后两者允许您实现超时、滴答声和其他基于时间的行为。
 * 
 * <p>
 * 为应用程序创建进程时，其主线程专用于运行消息队列，该队列负责管理顶级应用程序对象
 * （活动、广播接收器等）及其创建的任何窗口。您可以创建自己的线程，并通过处理程序与主应用程序线程通信。
 * 这是通过调用与以前相同的<em>post</em>或<em>sendMessage</em>方法来完成的，但是要从新线程调用。
 * 然后，给定的Runnable或消息将被安排在处理程序的消息队列中，并在适当时进行处理。
 */
public class Handler {
    /**
     * 将此标志设置为true可检测扩展此处理程序类且不是静态的匿名、本地或成员类。此类类可能会产生泄漏。
     */
    private static final boolean FIND_POTENTIAL_LEAKS = false;
    private static final String TAG = "Handler";
    private static Handler MAIN_THREAD_HANDLER = null;

    /**
     * 在实例化处理程序时可以使用回调接口，以避免实现自己的处理程序子类。
     */
    public interface Callback {
        /**
         * @param msg A {@link android.os.Message Message} object
         * @return 如果不需要进一步处理，则为True
         */
        public boolean handleMessage(Message msg);
    }
    
    /**
     * 子类必须实现这一点才能接收消息。
     */
    public void handleMessage(Message msg) {
    }
    
    /**
     * 在此处处理系统消息。
     */
    public void dispatchMessage(Message msg) {
    	// message中callback是一个Runnable对象，如果callback不为空，则直接调用callback的run方法
        if (msg.callback != null) {
            handleCallback(msg);
        } else {
        	// 如果 Handler 的 mCallback 不为null则调用 mCallback 的 handleMessage 将消息分发
            if (mCallback != null) {
                if (mCallback.handleMessage(msg)) {
                    return;
                }
            }
			// 上面两种情况都没有处理消息的回调，则调用自己的 handleMessage，如果当前类有覆写的子类则走到覆写的方法中
            handleMessage(msg);
        }
    }

    /**
     * 默认构造函数将此处理程序与当前线程的{@link Looper}相关联。
     * 如果此线程没有 Looper，则此处理程序将无法接收消息，因此会引发异常。
     */
    public Handler() {
        this(null, false);
    }

    /**
	 * 构造函数将此处理程序与当前线程的{@link Looper}相关联，并接受一个回调接口，您可以在其中处理消息。
	 * 如果此线程没有 Looper，则此处理程序将无法接收消息，因此会引发异常。
     *
     * @param callback 用来处理消息的回调接口，或为null。
     */
    public Handler(Callback callback) {
        this(callback, false);
    }

    /**
     * 使用提供的{@link Looper}而不是默认的。
     *
     * @param Looper 循环器，不能为空。
     */
    public Handler(Looper looper) {
        this(looper, null, false);
    }

    /**
	 * 使用提供的{@link Looper}而不是默认的，并使用回调接口来处理消息
     *
     * @param looper 	循环器，不能为空。
     * @param callback 	用来处理消息的回调接口，或为null。
     */
    public Handler(Looper looper, Callback callback) {
        this(looper, callback, false);
    }

    /**
	 * 对当前线程使用{@link Looper}，并设置处理程序是否应该是异步的。
     *
	 * 默认情况下，处理程序是同步的，除非使用此构造函数来生成严格异步的处理程序。
     *
     * 异步消息表示不需要对同步消息进行全局排序的中断或事件。
     * 异步消息不受 {@link MessageQueue#enqueueSyncBarrier(long)} 引入的同步障碍的限制。
     *
     * @param async 如果为true，则处理程序为发送到它的每个 {@link Message} 或发布到
     * 它的 {@link Runnable} 调用 {@link Message#setAsynchronous(boolean)} 。
     *
     * @hide
     */
    public Handler(boolean async) {
        this(null, async);
    }

    /**
     * Use the {@link Looper} for the current thread with the specified callback interface
     * and set whether the handler should be asynchronous.
     *
     * Handlers are synchronous by default unless this constructor is used to make
     * one that is strictly asynchronous.
     *
     * Asynchronous messages represent interrupts or events that do not require global ordering
     * with respect to synchronous messages.  Asynchronous messages are not subject to
     * the synchronization barriers introduced by {@link MessageQueue#enqueueSyncBarrier(long)}.
     *
     * @param callback The callback interface in which to handle messages, or null.
     * @param async If true, the handler calls {@link Message#setAsynchronous(boolean)} for
     * each {@link Message} that is sent to it or {@link Runnable} that is posted to it.
     *
     * @hide
     */
    public Handler(Callback callback, boolean async) {
        if (FIND_POTENTIAL_LEAKS) {
            final Class<? extends Handler> klass = getClass();
            if ((klass.isAnonymousClass() || klass.isMemberClass() || klass.isLocalClass()) &&
                    (klass.getModifiers() & Modifier.STATIC) == 0) {
                Log.w(TAG, "The following Handler class should be static or leaks might occur: " +
                    klass.getCanonicalName());
            }
        }
		// 获取Looper，**注意不是创建Looper**！
        mLooper = Looper.myLooper();
        if (mLooper == null) {
            throw new RuntimeException(
                "Can't create handler inside thread " + Thread.currentThread()
                        + " that has not called Looper.prepare()");
        }
		// 通过Looper获取消息队列MessageQueue
        mQueue = mLooper.mQueue;
		// 初始化了回调接口
        mCallback = callback;
        mAsynchronous = async;
    }

    /**
     * Use the provided {@link Looper} instead of the default one and take a callback
     * interface in which to handle messages.  Also set whether the handler
     * should be asynchronous.
     *
     * Handlers are synchronous by default unless this constructor is used to make
     * one that is strictly asynchronous.
     *
     * Asynchronous messages represent interrupts or events that do not require global ordering
     * with respect to synchronous messages.  Asynchronous messages are not subject to
     * the synchronization barriers introduced by conditions such as display vsync.
     *
     * @param looper The looper, must not be null.
     * @param callback The callback interface in which to handle messages, or null.
     * @param async If true, the handler calls {@link Message#setAsynchronous(boolean)} for
     * each {@link Message} that is sent to it or {@link Runnable} that is posted to it.
     *
     * @hide
     */
    public Handler(Looper looper, Callback callback, boolean async) {
        mLooper = looper;
        mQueue = looper.mQueue;
        mCallback = callback;
        mAsynchronous = async;
    }

    /**
     * Create a new Handler whose posted messages and runnables are not subject to
     * synchronization barriers such as display vsync.
     *
     * <p>Messages sent to an async handler are guaranteed to be ordered with respect to one another,
     * but not necessarily with respect to messages from other Handlers.</p>
     *
     * @see #createAsync(Looper, Callback) to create an async Handler with custom message handling.
     *
     * @param looper the Looper that the new Handler should be bound to
     * @return a new async Handler instance
     */
    @NonNull
    public static Handler createAsync(@NonNull Looper looper) {
        if (looper == null) throw new NullPointerException("looper must not be null");
        return new Handler(looper, null, true);
    }

    /**
     * Create a new Handler whose posted messages and runnables are not subject to
     * synchronization barriers such as display vsync.
     *
     * <p>Messages sent to an async handler are guaranteed to be ordered with respect to one another,
     * but not necessarily with respect to messages from other Handlers.</p>
     *
     * @see #createAsync(Looper) to create an async Handler without custom message handling.
     *
     * @param looper the Looper that the new Handler should be bound to
     * @return a new async Handler instance
     */
    @NonNull
    public static Handler createAsync(@NonNull Looper looper, @NonNull Callback callback) {
        if (looper == null) throw new NullPointerException("looper must not be null");
        if (callback == null) throw new NullPointerException("callback must not be null");
        return new Handler(looper, callback, true);
    }

    /** @hide */
    @NonNull
    public static Handler getMain() {
        if (MAIN_THREAD_HANDLER == null) {
            MAIN_THREAD_HANDLER = new Handler(Looper.getMainLooper());
        }
        return MAIN_THREAD_HANDLER;
    }

    /** @hide */
    @NonNull
    public static Handler mainIfNull(@Nullable Handler handler) {
        return handler == null ? getMain() : handler;
    }

    /** {@hide} */
    public String getTraceName(Message message) {
        final StringBuilder sb = new StringBuilder();
        sb.append(getClass().getName()).append(": ");
        if (message.callback != null) {
            sb.append(message.callback.getClass().getName());
        } else {
            sb.append("#").append(message.what);
        }
        return sb.toString();
    }

    /**
     * Returns a string representing the name of the specified message.
     * The default implementation will either return the class name of the
     * message callback if any, or the hexadecimal representation of the
     * message "what" field.
     *  
     * @param message The message whose name is being queried 
     */
    public String getMessageName(Message message) {
        if (message.callback != null) {
            return message.callback.getClass().getName();
        }
        return "0x" + Integer.toHexString(message.what);
    }

    /**
     * Returns a new {@link android.os.Message Message} from the global message pool. More efficient than
     * creating and allocating new instances. The retrieved message has its handler set to this instance (Message.target == this).
     *  If you don't want that facility, just call Message.obtain() instead.
     */
    public final Message obtainMessage()
    {
        return Message.obtain(this);
    }

    /**
     * Same as {@link #obtainMessage()}, except that it also sets the what member of the returned Message.
     * 
     * @param what Value to assign to the returned Message.what field.
     * @return A Message from the global message pool.
     */
    public final Message obtainMessage(int what)
    {
        return Message.obtain(this, what);
    }
    
    /**
     * 
     * Same as {@link #obtainMessage()}, except that it also sets the what and obj members 
     * of the returned Message.
     * 
     * @param what Value to assign to the returned Message.what field.
     * @param obj Value to assign to the returned Message.obj field.
     * @return A Message from the global message pool.
     */
    public final Message obtainMessage(int what, Object obj)
    {
        return Message.obtain(this, what, obj);
    }

    /**
     * 
     * Same as {@link #obtainMessage()}, except that it also sets the what, arg1 and arg2 members of the returned
     * Message.
     * @param what Value to assign to the returned Message.what field.
     * @param arg1 Value to assign to the returned Message.arg1 field.
     * @param arg2 Value to assign to the returned Message.arg2 field.
     * @return A Message from the global message pool.
     */
    public final Message obtainMessage(int what, int arg1, int arg2)
    {
        return Message.obtain(this, what, arg1, arg2);
    }
    
    /**
     * 
     * Same as {@link #obtainMessage()}, except that it also sets the what, obj, arg1,and arg2 values on the 
     * returned Message.
     * @param what Value to assign to the returned Message.what field.
     * @param arg1 Value to assign to the returned Message.arg1 field.
     * @param arg2 Value to assign to the returned Message.arg2 field.
     * @param obj Value to assign to the returned Message.obj field.
     * @return A Message from the global message pool.
     */
    public final Message obtainMessage(int what, int arg1, int arg2, Object obj)
    {
        return Message.obtain(this, what, arg1, arg2, obj);
    }

    /**
     * Causes the Runnable r to be added to the message queue.
     * The runnable will be run on the thread to which this handler is 
     * attached. 
     *  
     * @param r The Runnable that will be executed.
     * 
     * @return Returns true if the Runnable was successfully placed in to the 
     *         message queue.  Returns false on failure, usually because the
     *         looper processing the message queue is exiting.
     */
    public final boolean post(Runnable r)
    {
       return  sendMessageDelayed(getPostMessage(r), 0);
    }
    
    /**
     * Causes the Runnable r to be added to the message queue, to be run
     * at a specific time given by <var>uptimeMillis</var>.
     * <b>The time-base is {@link android.os.SystemClock#uptimeMillis}.</b>
     * Time spent in deep sleep will add an additional delay to execution.
     * The runnable will be run on the thread to which this handler is attached.
     *
     * @param r The Runnable that will be executed.
     * @param uptimeMillis The absolute time at which the callback should run,
     *         using the {@link android.os.SystemClock#uptimeMillis} time-base.
     *  
     * @return Returns true if the Runnable was successfully placed in to the 
     *         message queue.  Returns false on failure, usually because the
     *         looper processing the message queue is exiting.  Note that a
     *         result of true does not mean the Runnable will be processed -- if
     *         the looper is quit before the delivery time of the message
     *         occurs then the message will be dropped.
     */
    public final boolean postAtTime(Runnable r, long uptimeMillis)
    {
        return sendMessageAtTime(getPostMessage(r), uptimeMillis);
    }
    
    /**
     * Causes the Runnable r to be added to the message queue, to be run
     * at a specific time given by <var>uptimeMillis</var>.
     * <b>The time-base is {@link android.os.SystemClock#uptimeMillis}.</b>
     * Time spent in deep sleep will add an additional delay to execution.
     * The runnable will be run on the thread to which this handler is attached.
     *
     * @param r The Runnable that will be executed.
     * @param token An instance which can be used to cancel {@code r} via
     *         {@link #removeCallbacksAndMessages}.
     * @param uptimeMillis The absolute time at which the callback should run,
     *         using the {@link android.os.SystemClock#uptimeMillis} time-base.
     * 
     * @return Returns true if the Runnable was successfully placed in to the 
     *         message queue.  Returns false on failure, usually because the
     *         looper processing the message queue is exiting.  Note that a
     *         result of true does not mean the Runnable will be processed -- if
     *         the looper is quit before the delivery time of the message
     *         occurs then the message will be dropped.
     *         
     * @see android.os.SystemClock#uptimeMillis
     */
    public final boolean postAtTime(Runnable r, Object token, long uptimeMillis)
    {
        return sendMessageAtTime(getPostMessage(r, token), uptimeMillis);
    }
    
    /**
     * Causes the Runnable r to be added to the message queue, to be run
     * after the specified amount of time elapses.
     * The runnable will be run on the thread to which this handler
     * is attached.
     * <b>The time-base is {@link android.os.SystemClock#uptimeMillis}.</b>
     * Time spent in deep sleep will add an additional delay to execution.
     *  
     * @param r The Runnable that will be executed.
     * @param delayMillis The delay (in milliseconds) until the Runnable
     *        will be executed.
     *        
     * @return Returns true if the Runnable was successfully placed in to the 
     *         message queue.  Returns false on failure, usually because the
     *         looper processing the message queue is exiting.  Note that a
     *         result of true does not mean the Runnable will be processed --
     *         if the looper is quit before the delivery time of the message
     *         occurs then the message will be dropped.
     */
    public final boolean postDelayed(Runnable r, long delayMillis)
    {
        return sendMessageDelayed(getPostMessage(r), delayMillis);
    }
    
    /**
     * Causes the Runnable r to be added to the message queue, to be run
     * after the specified amount of time elapses.
     * The runnable will be run on the thread to which this handler
     * is attached.
     * <b>The time-base is {@link android.os.SystemClock#uptimeMillis}.</b>
     * Time spent in deep sleep will add an additional delay to execution.
     *
     * @param r The Runnable that will be executed.
     * @param token An instance which can be used to cancel {@code r} via
     *         {@link #removeCallbacksAndMessages}.
     * @param delayMillis The delay (in milliseconds) until the Runnable
     *        will be executed.
     *
     * @return Returns true if the Runnable was successfully placed in to the
     *         message queue.  Returns false on failure, usually because the
     *         looper processing the message queue is exiting.  Note that a
     *         result of true does not mean the Runnable will be processed --
     *         if the looper is quit before the delivery time of the message
     *         occurs then the message will be dropped.
     */
    public final boolean postDelayed(Runnable r, Object token, long delayMillis)
    {
        return sendMessageDelayed(getPostMessage(r, token), delayMillis);
    }

    /**
     * Posts a message to an object that implements Runnable.
     * Causes the Runnable r to executed on the next iteration through the
     * message queue. The runnable will be run on the thread to which this
     * handler is attached.
     * <b>This method is only for use in very special circumstances -- it
     * can easily starve the message queue, cause ordering problems, or have
     * other unexpected side-effects.</b>
     *  
     * @param r The Runnable that will be executed.
     * 
     * @return Returns true if the message was successfully placed in to the 
     *         message queue.  Returns false on failure, usually because the
     *         looper processing the message queue is exiting.
     */
    public final boolean postAtFrontOfQueue(Runnable r)
    {
        return sendMessageAtFrontOfQueue(getPostMessage(r));
    }

    /**
     * Runs the specified task synchronously.
     * <p>
     * If the current thread is the same as the handler thread, then the runnable
     * runs immediately without being enqueued.  Otherwise, posts the runnable
     * to the handler and waits for it to complete before returning.
     * </p><p>
     * This method is dangerous!  Improper use can result in deadlocks.
     * Never call this method while any locks are held or use it in a
     * possibly re-entrant manner.
     * </p><p>
     * This method is occasionally useful in situations where a background thread
     * must synchronously await completion of a task that must run on the
     * handler's thread.  However, this problem is often a symptom of bad design.
     * Consider improving the design (if possible) before resorting to this method.
     * </p><p>
     * One example of where you might want to use this method is when you just
     * set up a Handler thread and need to perform some initialization steps on
     * it before continuing execution.
     * </p><p>
     * If timeout occurs then this method returns <code>false</code> but the runnable
     * will remain posted on the handler and may already be in progress or
     * complete at a later time.
     * </p><p>
     * When using this method, be sure to use {@link Looper#quitSafely} when
     * quitting the looper.  Otherwise {@link #runWithScissors} may hang indefinitely.
     * (TODO: We should fix this by making MessageQueue aware of blocking runnables.)
     * </p>
     *
     * @param r The Runnable that will be executed synchronously.
     * @param timeout The timeout in milliseconds, or 0 to wait indefinitely.
     *
     * @return Returns true if the Runnable was successfully executed.
     *         Returns false on failure, usually because the
     *         looper processing the message queue is exiting.
     *
     * @hide This method is prone to abuse and should probably not be in the API.
     * If we ever do make it part of the API, we might want to rename it to something
     * less funny like runUnsafe().
     */
    public final boolean runWithScissors(final Runnable r, long timeout) {
        if (r == null) {
            throw new IllegalArgumentException("runnable must not be null");
        }
        if (timeout < 0) {
            throw new IllegalArgumentException("timeout must be non-negative");
        }

        if (Looper.myLooper() == mLooper) {
            r.run();
            return true;
        }

        BlockingRunnable br = new BlockingRunnable(r);
        return br.postAndWait(this, timeout);
    }

    /**
     * Remove any pending posts of Runnable r that are in the message queue.
     */
    public final void removeCallbacks(Runnable r)
    {
        mQueue.removeMessages(this, r, null);
    }

    /**
     * Remove any pending posts of Runnable <var>r</var> with Object
     * <var>token</var> that are in the message queue.  If <var>token</var> is null,
     * all callbacks will be removed.
     */
    public final void removeCallbacks(Runnable r, Object token)
    {
        mQueue.removeMessages(this, r, token);
    }

    /**
     * Pushes a message onto the end of the message queue after all pending messages
     * before the current time. It will be received in {@link #handleMessage},
     * in the thread attached to this handler.
     *  
     * @return Returns true if the message was successfully placed in to the 
     *         message queue.  Returns false on failure, usually because the
     *         looper processing the message queue is exiting.
     */
    public final boolean sendMessage(Message msg)
    {
        return sendMessageDelayed(msg, 0);
    }

    /**
     * Sends a Message containing only the what value.
     *  
     * @return Returns true if the message was successfully placed in to the 
     *         message queue.  Returns false on failure, usually because the
     *         looper processing the message queue is exiting.
     */
    public final boolean sendEmptyMessage(int what)
    {
        return sendEmptyMessageDelayed(what, 0);
    }

    /**
     * Sends a Message containing only the what value, to be delivered
     * after the specified amount of time elapses.
     * @see #sendMessageDelayed(android.os.Message, long) 
     * 
     * @return Returns true if the message was successfully placed in to the 
     *         message queue.  Returns false on failure, usually because the
     *         looper processing the message queue is exiting.
     */
    public final boolean sendEmptyMessageDelayed(int what, long delayMillis) {
        Message msg = Message.obtain();
        msg.what = what;
        return sendMessageDelayed(msg, delayMillis);
    }

    /**
     * Sends a Message containing only the what value, to be delivered 
     * at a specific time.
     * @see #sendMessageAtTime(android.os.Message, long)
     *  
     * @return Returns true if the message was successfully placed in to the 
     *         message queue.  Returns false on failure, usually because the
     *         looper processing the message queue is exiting.
     */

    public final boolean sendEmptyMessageAtTime(int what, long uptimeMillis) {
        Message msg = Message.obtain();
        msg.what = what;
        return sendMessageAtTime(msg, uptimeMillis);
    }

    /**
     * Enqueue a message into the message queue after all pending messages
     * before (current time + delayMillis). You will receive it in
     * {@link #handleMessage}, in the thread attached to this handler.
     *  
     * @return Returns true if the message was successfully placed in to the 
     *         message queue.  Returns false on failure, usually because the
     *         looper processing the message queue is exiting.  Note that a
     *         result of true does not mean the message will be processed -- if
     *         the looper is quit before the delivery time of the message
     *         occurs then the message will be dropped.
     */
    public final boolean sendMessageDelayed(Message msg, long delayMillis)
    {
        if (delayMillis < 0) {
            delayMillis = 0;
        }
        return sendMessageAtTime(msg, SystemClock.uptimeMillis() + delayMillis);
    }

    /**
     * Enqueue a message into the message queue after all pending messages
     * before the absolute time (in milliseconds) <var>uptimeMillis</var>.
     * <b>The time-base is {@link android.os.SystemClock#uptimeMillis}.</b>
     * Time spent in deep sleep will add an additional delay to execution.
     * You will receive it in {@link #handleMessage}, in the thread attached
     * to this handler.
     * 
     * @param uptimeMillis The absolute time at which the message should be
     *         delivered, using the
     *         {@link android.os.SystemClock#uptimeMillis} time-base.
     *         
     * @return Returns true if the message was successfully placed in to the 
     *         message queue.  Returns false on failure, usually because the
     *         looper processing the message queue is exiting.  Note that a
     *         result of true does not mean the message will be processed -- if
     *         the looper is quit before the delivery time of the message
     *         occurs then the message will be dropped.
     */
    public boolean sendMessageAtTime(Message msg, long uptimeMillis) {
        MessageQueue queue = mQueue;
        if (queue == null) {
            RuntimeException e = new RuntimeException(
                    this + " sendMessageAtTime() called with no mQueue");
            Log.w("Looper", e.getMessage(), e);
            return false;
        }
        return enqueueMessage(queue, msg, uptimeMillis);
    }

    /**
     * Enqueue a message at the front of the message queue, to be processed on
     * the next iteration of the message loop.  You will receive it in
     * {@link #handleMessage}, in the thread attached to this handler.
     * <b>This method is only for use in very special circumstances -- it
     * can easily starve the message queue, cause ordering problems, or have
     * other unexpected side-effects.</b>
     *  
     * @return Returns true if the message was successfully placed in to the 
     *         message queue.  Returns false on failure, usually because the
     *         looper processing the message queue is exiting.
     */
    public final boolean sendMessageAtFrontOfQueue(Message msg) {
        MessageQueue queue = mQueue;
        if (queue == null) {
            RuntimeException e = new RuntimeException(
                this + " sendMessageAtTime() called with no mQueue");
            Log.w("Looper", e.getMessage(), e);
            return false;
        }
        return enqueueMessage(queue, msg, 0);
    }

    /**
     * Executes the message synchronously if called on the same thread this handler corresponds to,
     * or {@link #sendMessage pushes it to the queue} otherwise
     *
     * @return Returns true if the message was successfully ran or placed in to the
     *         message queue.  Returns false on failure, usually because the
     *         looper processing the message queue is exiting.
     * @hide
     */
    public final boolean executeOrSendMessage(Message msg) {
        if (mLooper == Looper.myLooper()) {
            dispatchMessage(msg);
            return true;
        }
        return sendMessage(msg);
    }

	/**
	 * 创建Message的时候可以通过 Message.obtain(Handler h) 这个构造方法绑定。当然可以在 在Handler 中的
	 * enqueueMessage（）也绑定了，所有发送Message的方法都会调用此方法入队，所以在创建Message的时候是可以不绑定的
	 */
    private boolean enqueueMessage(MessageQueue queue, Message msg, long uptimeMillis) {
        msg.target = this;
        if (mAsynchronous) {
            msg.setAsynchronous(true);
        }
        return queue.enqueueMessage(msg, uptimeMillis);
    }

    /**
     * Remove any pending posts of messages with code 'what' that are in the
     * message queue.
     */
    public final void removeMessages(int what) {
        mQueue.removeMessages(this, what, null);
    }

    /**
     * Remove any pending posts of messages with code 'what' and whose obj is
     * 'object' that are in the message queue.  If <var>object</var> is null,
     * all messages will be removed.
     */
    public final void removeMessages(int what, Object object) {
        mQueue.removeMessages(this, what, object);
    }

    /**
     * Remove any pending posts of callbacks and sent messages whose
     * <var>obj</var> is <var>token</var>.  If <var>token</var> is null,
     * all callbacks and messages will be removed.
     */
    public final void removeCallbacksAndMessages(Object token) {
        mQueue.removeCallbacksAndMessages(this, token);
    }

    /**
     * Check if there are any pending posts of messages with code 'what' in
     * the message queue.
     */
    public final boolean hasMessages(int what) {
        return mQueue.hasMessages(this, what, null);
    }

    /**
     * Return whether there are any messages or callbacks currently scheduled on this handler.
     * @hide
     */
    public final boolean hasMessagesOrCallbacks() {
        return mQueue.hasMessages(this);
    }

    /**
     * Check if there are any pending posts of messages with code 'what' and
     * whose obj is 'object' in the message queue.
     */
    public final boolean hasMessages(int what, Object object) {
        return mQueue.hasMessages(this, what, object);
    }

    /**
     * Check if there are any pending posts of messages with callback r in
     * the message queue.
     * 
     * @hide
     */
    public final boolean hasCallbacks(Runnable r) {
        return mQueue.hasMessages(this, r, null);
    }

    // if we can get rid of this method, the handler need not remember its loop
    // we could instead export a getMessageQueue() method... 
    public final Looper getLooper() {
        return mLooper;
    }

    public final void dump(Printer pw, String prefix) {
        pw.println(prefix + this + " @ " + SystemClock.uptimeMillis());
        if (mLooper == null) {
            pw.println(prefix + "looper uninitialized");
        } else {
            mLooper.dump(pw, prefix + "  ");
        }
    }

    /**
     * @hide
     */
    public final void dumpMine(Printer pw, String prefix) {
        pw.println(prefix + this + " @ " + SystemClock.uptimeMillis());
        if (mLooper == null) {
            pw.println(prefix + "looper uninitialized");
        } else {
            mLooper.dump(pw, prefix + "  ", this);
        }
    }

    @Override
    public String toString() {
        return "Handler (" + getClass().getName() + ") {"
        + Integer.toHexString(System.identityHashCode(this))
        + "}";
    }

    final IMessenger getIMessenger() {
        synchronized (mQueue) {
            if (mMessenger != null) {
                return mMessenger;
            }
            mMessenger = new MessengerImpl();
            return mMessenger;
        }
    }

    private final class MessengerImpl extends IMessenger.Stub {
        public void send(Message msg) {
            msg.sendingUid = Binder.getCallingUid();
            Handler.this.sendMessage(msg);
        }
    }

    private static Message getPostMessage(Runnable r) {
        Message m = Message.obtain();
        m.callback = r;
        return m;
    }

    private static Message getPostMessage(Runnable r, Object token) {
        Message m = Message.obtain();
        m.obj = token;
        m.callback = r;
        return m;
    }

    private static void handleCallback(Message message) {
        message.callback.run();
    }

    final Looper mLooper;
    final MessageQueue mQueue;
    final Callback mCallback;
    final boolean mAsynchronous;
    IMessenger mMessenger;

    private static final class BlockingRunnable implements Runnable {
        private final Runnable mTask;
        private boolean mDone;

        public BlockingRunnable(Runnable task) {
            mTask = task;
        }

        @Override
        public void run() {
            try {
                mTask.run();
            } finally {
                synchronized (this) {
                    mDone = true;
                    notifyAll();
                }
            }
        }

        public boolean postAndWait(Handler handler, long timeout) {
            if (!handler.post(this)) {
                return false;
            }

            synchronized (this) {
                if (timeout > 0) {
                    final long expirationTime = SystemClock.uptimeMillis() + timeout;
                    while (!mDone) {
                        long delay = expirationTime - SystemClock.uptimeMillis();
                        if (delay <= 0) {
                            return false; // timeout
                        }
                        try {
                            wait(delay);
                        } catch (InterruptedException ex) {
                        }
                    }
                } else {
                    while (!mDone) {
                        try {
                            wait();
                        } catch (InterruptedException ex) {
                        }
                    }
                }
            }
            return true;
        }
    }
}
