/*
 * Copyright (C) 2007 The Android Open Source Project
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

package android.view;

import android.annotation.LayoutRes;
import android.annotation.Nullable;
import android.annotation.SystemService;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Message;
import android.os.Trace;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.util.Xml;
import android.widget.FrameLayout;

import com.android.internal.R;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.HashMap;

/**
 * Instantiates a layout XML file into its corresponding {@link android.view.View}
 * objects. It is never used directly. Instead, use
 * {@link android.app.Activity#getLayoutInflater()} or
 * {@link Context#getSystemService} to retrieve a standard LayoutInflater instance
 * that is already hooked up to the current context and correctly configured
 * for the device you are running on.
 *
 * <p>
 * To create a new LayoutInflater with an additional {@link Factory} for your
 * own views, you can use {@link #cloneInContext} to clone an existing
 * ViewFactory, and then call {@link #setFactory} on it to include your
 * Factory.
 *
 * <p>
 * For performance reasons, view inflation relies heavily on pre-processing of
 * XML files that is done at build time. Therefore, it is not currently possible
 * to use LayoutInflater with an XmlPullParser over a plain XML file at runtime;
 * it only works with an XmlPullParser returned from a compiled resource
 * (R.<em>something</em> file.)
 */
@SystemService(Context.LAYOUT_INFLATER_SERVICE)
public abstract class LayoutInflater {

    private static final String TAG = LayoutInflater.class.getSimpleName();
    private static final boolean DEBUG = false;

    /** Empty stack trace used to avoid log spam in re-throw exceptions. */
    private static final StackTraceElement[] EMPTY_STACK_TRACE = new StackTraceElement[0];

    /**
     * This field should be made private, so it is hidden from the SDK.
     * {@hide}
     */
    protected final Context mContext;

    // these are optional, set by the caller
    private boolean mFactorySet;
    private Factory mFactory;
    private Factory2 mFactory2;
    private Factory2 mPrivateFactory;
    private Filter mFilter;

	/**
	 * mConstructorArgs保存了构造方法的参数值信息，第一个是context，第二个是attr
	 * 也就是view的第二个构造方法参数
	 */
    final Object[] mConstructorArgs = new Object[2];

    static final Class<?>[] mConstructorSignature = new Class[] {
            Context.class, AttributeSet.class};

	/**
	 * 缓存解析xml中 View 的构造器
	 */
    private static final HashMap<String, Constructor<? extends View>> sConstructorMap =
            new HashMap<String, Constructor<? extends View>>();
	/**
	 * 缓存View 状态的过滤器
	 */
    private HashMap<String, Boolean> mFilterMap;

    private TypedValue mTempValue;

    private static final String TAG_MERGE = "merge";
    private static final String TAG_INCLUDE = "include";
    private static final String TAG_1995 = "blink";
    private static final String TAG_REQUEST_FOCUS = "requestFocus";
    private static final String TAG_TAG = "tag";

    private static final String ATTR_LAYOUT = "layout";

    private static final int[] ATTRS_THEME = new int[] {
            com.android.internal.R.attr.theme };

    /**
     * Hook to allow clients of the LayoutInflater to restrict the set of Views that are allowed
     * to be inflated.
     *
     */
    public interface Filter {
        /**
         * Hook to allow clients of the LayoutInflater to restrict the set of Views
         * that are allowed to be inflated.
         *
         * @param clazz The class object for the View that is about to be inflated
         *
         * @return True if this class is allowed to be inflated, or false otherwise
         */
        @SuppressWarnings("unchecked")
        boolean onLoadClass(Class clazz);
    }

    public interface Factory {
        /**
         * Hook you can supply that is called when inflating from a LayoutInflater.
         * You can use this to customize the tag names available in your XML
         * layout files.
         *
         * <p>
         * Note that it is good practice to prefix these custom names with your
         * package (i.e., com.coolcompany.apps) to avoid conflicts with system
         * names.
         *
         * @param name Tag name to be inflated.
         * @param context The context the view is being created in.
         * @param attrs Inflation attributes as specified in XML file.
         *
         * @return View Newly created view. Return null for the default
         *         behavior.
         */
        public View onCreateView(String name, Context context, AttributeSet attrs);
    }

    public interface Factory2 extends Factory {
        /**
         * Version of {@link #onCreateView(String, Context, AttributeSet)}
         * that also supplies the parent that the view created view will be
         * placed in.
         *
         * @param parent The parent that the created view will be placed
         * in; <em>note that this may be null</em>.
         * @param name Tag name to be inflated.
         * @param context The context the view is being created in.
         * @param attrs Inflation attributes as specified in XML file.
         *
         * @return View Newly created view. Return null for the default
         *         behavior.
         */
        public View onCreateView(View parent, String name, Context context, AttributeSet attrs);
    }

    private static class FactoryMerger implements Factory2 {
        private final Factory mF1, mF2;
        private final Factory2 mF12, mF22;

        FactoryMerger(Factory f1, Factory2 f12, Factory f2, Factory2 f22) {
            mF1 = f1;
            mF2 = f2;
            mF12 = f12;
            mF22 = f22;
        }

        public View onCreateView(String name, Context context, AttributeSet attrs) {
            View v = mF1.onCreateView(name, context, attrs);
            if (v != null) return v;
            return mF2.onCreateView(name, context, attrs);
        }

        public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
            View v = mF12 != null ? mF12.onCreateView(parent, name, context, attrs)
                    : mF1.onCreateView(name, context, attrs);
            if (v != null) return v;
            return mF22 != null ? mF22.onCreateView(parent, name, context, attrs)
                    : mF2.onCreateView(name, context, attrs);
        }
    }

    /**
     * Create a new LayoutInflater instance associated with a particular Context.
     * Applications will almost always want to use
     * {@link Context#getSystemService Context.getSystemService()} to retrieve
     * the standard {@link Context#LAYOUT_INFLATER_SERVICE Context.INFLATER_SERVICE}.
     *
     * @param context The Context in which this LayoutInflater will create its
     * Views; most importantly, this supplies the theme from which the default
     * values for their attributes are retrieved.
     */
    protected LayoutInflater(Context context) {
        mContext = context;
    }

    /**
     * Create a new LayoutInflater instance that is a copy of an existing
     * LayoutInflater, optionally with its Context changed.  For use in
     * implementing {@link #cloneInContext}.
     *
     * @param original The original LayoutInflater to copy.
     * @param newContext The new Context to use.
     */
    protected LayoutInflater(LayoutInflater original, Context newContext) {
        mContext = newContext;
        mFactory = original.mFactory;
        mFactory2 = original.mFactory2;
        mPrivateFactory = original.mPrivateFactory;
        setFilter(original.mFilter);
    }

    /**
     * Obtains the LayoutInflater from the given context.
     */
    public static LayoutInflater from(Context context) {
        LayoutInflater LayoutInflater =
                (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (LayoutInflater == null) {
            throw new AssertionError("LayoutInflater not found.");
        }
        return LayoutInflater;
    }

    /**
     * Create a copy of the existing LayoutInflater object, with the copy
     * pointing to a different Context than the original.  This is used by
     * {@link ContextThemeWrapper} to create a new LayoutInflater to go along
     * with the new Context theme.
     *
     * @param newContext The new Context to associate with the new LayoutInflater.
     * May be the same as the original Context if desired.
     *
     * @return Returns a brand spanking new LayoutInflater object associated with
     * the given Context.
     */
    public abstract LayoutInflater cloneInContext(Context newContext);

    /**
     * Return the context we are running in, for access to resources, class
     * loader, etc.
     */
    public Context getContext() {
        return mContext;
    }

    /**
     * Return the current {@link Factory} (or null). This is called on each element
     * name. If the factory returns a View, add that to the hierarchy. If it
     * returns null, proceed to call onCreateView(name).
     */
    public final Factory getFactory() {
        return mFactory;
    }

    /**
     * Return the current {@link Factory2}.  Returns null if no factory is set
     * or the set factory does not implement the {@link Factory2} interface.
     * This is called on each element
     * name. If the factory returns a View, add that to the hierarchy. If it
     * returns null, proceed to call onCreateView(name).
     */
    public final Factory2 getFactory2() {
        return mFactory2;
    }

    /**
     * Attach a custom Factory interface for creating views while using
     * this LayoutInflater.  This must not be null, and can only be set once;
     * after setting, you can not change the factory.  This is
     * called on each element name as the xml is parsed. If the factory returns
     * a View, that is added to the hierarchy. If it returns null, the next
     * factory default {@link #onCreateView} method is called.
     *
     * <p>If you have an existing
     * LayoutInflater and want to add your own factory to it, use
     * {@link #cloneInContext} to clone the existing instance and then you
     * can use this function (once) on the returned new instance.  This will
     * merge your own factory with whatever factory the original instance is
     * using.
     */
    public void setFactory(Factory factory) {
        if (mFactorySet) {
            throw new IllegalStateException("A factory has already been set on this LayoutInflater");
        }
        if (factory == null) {
            throw new NullPointerException("Given factory can not be null");
        }
        mFactorySet = true;
        if (mFactory == null) {
            mFactory = factory;
        } else {
            mFactory = new FactoryMerger(factory, null, mFactory, mFactory2);
        }
    }

    /**
     * Like {@link #setFactory}, but allows you to set a {@link Factory2}
     * interface.
     */
    public void setFactory2(Factory2 factory) {
        if (mFactorySet) {
            throw new IllegalStateException("A factory has already been set on this LayoutInflater");
        }
        if (factory == null) {
            throw new NullPointerException("Given factory can not be null");
        }
        mFactorySet = true;
        if (mFactory == null) {
            mFactory = mFactory2 = factory;
        } else {
            mFactory = mFactory2 = new FactoryMerger(factory, factory, mFactory, mFactory2);
        }
    }

    /**
     * @hide for use by framework
     */
    public void setPrivateFactory(Factory2 factory) {
        if (mPrivateFactory == null) {
            mPrivateFactory = factory;
        } else {
            mPrivateFactory = new FactoryMerger(factory, factory, mPrivateFactory, mPrivateFactory);
        }
    }

    /**
     * @return The {@link Filter} currently used by this LayoutInflater to restrict the set of Views
     * that are allowed to be inflated.
     */
    public Filter getFilter() {
        return mFilter;
    }

    /**
     * Sets the {@link Filter} to by this LayoutInflater. If a view is attempted to be inflated
     * which is not allowed by the {@link Filter}, the {@link #inflate(int, ViewGroup)} call will
     * throw an {@link InflateException}. This filter will replace any previous filter set on this
     * LayoutInflater.
     *
     * @param filter The Filter which restricts the set of Views that are allowed to be inflated.
     *        This filter will replace any previous filter set on this LayoutInflater.
     */
    public void setFilter(Filter filter) {
        mFilter = filter;
        if (filter != null) {
            mFilterMap = new HashMap<String, Boolean>();
        }
    }

    /**
     * Inflate a new view hierarchy from the specified xml resource. Throws
     * {@link InflateException} if there is an error.
     *
     * @param resource ID for an XML layout resource to load (e.g.,
     *        <code>R.layout.main_page</code>)
     * @param root Optional view to be the parent of the generated hierarchy.
     * @return The root View of the inflated hierarchy. If root was supplied,
     *         this is the root View; otherwise it is the root of the inflated
     *         XML file.
     */
    public View inflate(@LayoutRes int resource, @Nullable ViewGroup root) {
        return inflate(resource, root, root != null);
    }

    /**
     * Inflate a new view hierarchy from the specified xml node. Throws
     * {@link InflateException} if there is an error. *
     * <p>
     * <em><strong>Important</strong></em>&nbsp;&nbsp;&nbsp;For performance
     * reasons, view inflation relies heavily on pre-processing of XML files
     * that is done at build time. Therefore, it is not currently possible to
     * use LayoutInflater with an XmlPullParser over a plain XML file at runtime.
     *
     * @param parser XML dom node containing the description of the view
     *        hierarchy.
     * @param root Optional view to be the parent of the generated hierarchy.
     * @return The root View of the inflated hierarchy. If root was supplied,
     *         this is the root View; otherwise it is the root of the inflated
     *         XML file.
     */
    public View inflate(XmlPullParser parser, @Nullable ViewGroup root) {
        return inflate(parser, root, root != null);
    }

    /**
     * Inflate a new view hierarchy from the specified xml resource. Throws
     * {@link InflateException} if there is an error.
     *
     * @param resource ID for an XML layout resource to load (e.g.,
     *        <code>R.layout.main_page</code>)
     * @param root Optional view to be the parent of the generated hierarchy (if
     *        <em>attachToRoot</em> is true), or else simply an object that
     *        provides a set of LayoutParams values for root of the returned
     *        hierarchy (if <em>attachToRoot</em> is false.)
     * @param attachToRoot Whether the inflated hierarchy should be attached to
     *        the root parameter? If false, root is only used to create the
     *        correct subclass of LayoutParams for the root view in the XML.
     * @return The root View of the inflated hierarchy. If root was supplied and
     *         attachToRoot is true, this is root; otherwise it is the root of
     *         the inflated XML file.
     */
    public View inflate(@LayoutRes int resource, @Nullable ViewGroup root, boolean attachToRoot) {
        final Resources res = getContext().getResources();
        if (DEBUG) {
            Log.d(TAG, "INFLATING from resource: \"" + res.getResourceName(resource) + "\" ("
                    + Integer.toHexString(resource) + ")");
        }
		/**
		 * 获取解析器XmlResourceParser：它包含解析后xml布局信息，
		 * 通过它，可以获得xml中各种标签的信息，甚至你可以简化的看做是一个包含xml格式字符串的缓存对象
		 */
        final XmlResourceParser parser = res.getLayout(resource);
        try {
			/**
			 * 调用重载方法
			 */
            return inflate(parser, root, attachToRoot);
        } finally {
            parser.close();
        }
    }

    /**
     * Inflate a new view hierarchy from the specified XML node. Throws
     * {@link InflateException} if there is an error.
     * <p>
     * <em><strong>Important</strong></em>&nbsp;&nbsp;&nbsp;For performance
     * reasons, view inflation relies heavily on pre-processing of XML files
     * that is done at build time. Therefore, it is not currently possible to
     * use LayoutInflater with an XmlPullParser over a plain XML file at runtime.
     *
     * @param parser XML dom node containing the description of the view
     *        hierarchy.
     * @param root Optional view to be the parent of the generated hierarchy (if
     *        <em>attachToRoot</em> is true), or else simply an object that
     *        provides a set of LayoutParams values for root of the returned
     *        hierarchy (if <em>attachToRoot</em> is false.)
     * @param attachToRoot Whether the inflated hierarchy should be attached to
     *        the root parameter? If false, root is only used to create the
     *        correct subclass of LayoutParams for the root view in the XML.
     * @return The root View of the inflated hierarchy. If root was supplied and
     *         attachToRoot is true, this is root; otherwise it is the root of
     *         the inflated XML file.
     */
    public View inflate(XmlPullParser parser, @Nullable ViewGroup root, boolean attachToRoot) {
        synchronized (mConstructorArgs) {
            Trace.traceBegin(Trace.TRACE_TAG_VIEW, "inflate");
			// mContext是通过LayoutInflater的构造方法传进来的的context，这里一般不调用构造方法，所以基本是null
            final Context inflaterContext = mContext;
            final AttributeSet attrs = Xml.asAttributeSet(parser);
			// mConstructorArgs保存了构造方法的参数值信息，第一个是context，第二个是attr
            // 这里先将参数的lastContext临时保存，在最后的位置再进行恢复
            Context lastContext = (Context) mConstructorArgs[0];
            mConstructorArgs[0] = inflaterContext;
			// 解析以后最终的View树
            View result = root;

            try {
                // Look for the root node.
                int type;
                while ((type = parser.next()) != XmlPullParser.START_TAG &&
                        type != XmlPullParser.END_DOCUMENT) {
                    // Empty
                }

                if (type != XmlPullParser.START_TAG) {
                    throw new InflateException(parser.getPositionDescription()
                            + ": No start tag found!");
                }
				//name是根节点标签
                final String name = parser.getName();

                if (DEBUG) {
                    System.out.println("**************************");
                    System.out.println("Creating root view: "
                            + name);
                    System.out.println("**************************");
                }
				// 根布局是Merge标签
                if (TAG_MERGE.equals(name)) {
					// merge标签必须嵌套到对应的父布局中来使用。所以如果root是空或者merge不绑定到布局中，那么就报错
                    if (root == null || !attachToRoot) {
                        throw new InflateException("<merge /> can be used only with a valid "
                                + "ViewGroup root and attachToRoot=true");
                    }
					// 进行布局文件的绘制
                    rInflate(parser, root, inflaterContext, attrs, false);
                } else {
                    // Temp is the root view that was found in the xml
                    /**
                     * 通过标签来创建View
                     */
                    final View temp = createViewFromTag(root, name, inflaterContext, attrs);

                    ViewGroup.LayoutParams params = null;

                    if (root != null) {
                        if (DEBUG) {
                            System.out.println("Creating params from root: " +
                                    root);
                        }
                        // Create layout params that match root, if supplied
                        /**
                         * 根据要创建的布局的属性以及root的信息，生成布局对应的params信息
                         */
                        params = root.generateLayoutParams(attrs);
                        if (!attachToRoot) {
                            // Set the layout params for temp if we are not
                            // attaching. (If we are, we use addView, below)
                            /**
                             * 根据attachToRoot标志，来判断是否将相关布局参数设置给xml的RootView
                             * 我们一般会传入false，就是说它会通过generateLayoutParams解析xml中RootView的参数，然后进行设置
                             * 如果传入true，那么则需要我们手动听过代码设置根布局的参数（也就是需要动态添加view和参数）
                             */
                            temp.setLayoutParams(params);
                        }
                    }

                    if (DEBUG) {
                        System.out.println("-----> start inflating children");
                    }

                    // Inflate all children under temp against its context.
                    // 绘制子控件，并添加到temp中
                    rInflateChildren(parser, temp, attrs, true);

                    if (DEBUG) {
                        System.out.println("-----> done inflating children");
                    }

                    // We are supposed to attach all the views we found (int temp)
                    // to root. Do that now.
                    // 如果设置了root，并且要添加到父控件，那么就进行rooot.addView操作
                    if (root != null && attachToRoot) {
                        root.addView(temp, params);
                    }

                    // Decide whether to return the root that was passed in or the
                    // top view found in xml.
                    // 如果root为空或者不添加到父控件。则直接返回绘制的view
                    if (root == null || !attachToRoot) {
                        result = temp;
                    }
                }

            } catch (XmlPullParserException e) {
                final InflateException ie = new InflateException(e.getMessage(), e);
                ie.setStackTrace(EMPTY_STACK_TRACE);
                throw ie;
            } catch (Exception e) {
                final InflateException ie = new InflateException(parser.getPositionDescription()
                        + ": " + e.getMessage(), e);
                ie.setStackTrace(EMPTY_STACK_TRACE);
                throw ie;
            } finally {
                // Don't retain static reference on context.
                mConstructorArgs[0] = lastContext;
                mConstructorArgs[1] = null;

                Trace.traceEnd(Trace.TRACE_TAG_VIEW);
            }

            return result;
        }
    }

    private static final ClassLoader BOOT_CLASS_LOADER = LayoutInflater.class.getClassLoader();

    private final boolean verifyClassLoader(Constructor<? extends View> constructor) {
		// 通过view 的构造器拿到Class对象 再拿到ClassLoader
        final ClassLoader constructorLoader = constructor.getDeclaringClass().getClassLoader();
        if (constructorLoader == BOOT_CLASS_LOADER) {
            // fast path for boot class loader (most common case?) - always ok
            // 引导类加载程序的快速路径（最常见的情况？）-总是好的
            return true;
        }
        // in all normal cases (no dynamic code loading), we will exit the following loop on the
        // first iteration (i.e. when the declaring classloader is the contexts class loader).
        // 在所有正常情况下（没有动态代码加载），我们将在第一次迭代时退出以下循环（即，当声明的类加载器是上下文类加载器时）。
        ClassLoader cl = mContext.getClassLoader();
        do {
            if (constructorLoader == cl) {
                return true;
            }
            cl = cl.getParent();
        } while (cl != null);
        return false;
    }

    /**
     * Low-level function for instantiating a view by name. This attempts to
     * instantiate a view class of the given <var>name</var> found in this
     * LayoutInflater's ClassLoader.
     *
     * <p>
     * There are two things that can happen in an error case: either the
     * exception describing the error will be thrown, or a null will be
     * returned. You must deal with both possibilities -- the former will happen
     * the first time createView() is called for a class of a particular name,
     * the latter every time there-after for that class name.
     *
     * @param name The full name of the class to be instantiated.
     * @param attrs The XML attributes supplied for this instance.
     *
     * @return View The newly instantiated view, or null.
     */
    public final View createView(String name, String prefix, AttributeSet attrs)
            throws ClassNotFoundException, InflateException {
        // 从缓存中获取构造方法
        Constructor<? extends View> constructor = sConstructorMap.get(name);
        if (constructor != null && !verifyClassLoader(constructor)) {
			// 如果缓存的类加载器和根加载器中的不一致
            constructor = null;
            sConstructorMap.remove(name);
        }
        Class<? extends View> clazz = null;

        try {
            Trace.traceBegin(Trace.TRACE_TAG_VIEW, name);

			/**
			 * 1.缓存中没有找到相同的类加载器
			 * 2.以name加载相应view 的Class
			 * 3.检测Class是否允许使用，不允许抛出异常，允许获取构造器，并添加到缓存中
			 */
            if (constructor == null) {
                // Class not found in the cache, see if it's real, and try to add it
                // 获取clazz文件
                clazz = mContext.getClassLoader().loadClass(
                        prefix != null ? (prefix + name) : name).asSubclass(View.class);

				// 检查 clazz，如果allowed为false则抛出异常
                if (mFilter != null && clazz != null) {
                    boolean allowed = mFilter.onLoadClass(clazz);
                    if (!allowed) {
                        failNotAllowed(name, prefix, attrs);
                    }
                }
				/**
				 * 获取构造方法，获取的是参数为(Context.class, AttributeSet)的那个构造方法
				 * 这就是为什么自定义控件必须覆写第二个构造方法
				 */
                constructor = clazz.getConstructor(mConstructorSignature);
                constructor.setAccessible(true);
                sConstructorMap.put(name, constructor);
				/**
				 * 因为else中证明 sConstructorMap 中是有缓存的，
				 * 所以只需要从状态缓存中去查看当前 clazz 的允许Inflated的状态
				 * 否则就抛出异常
				 */
            } else {
                // If we have a filter, apply it to cached constructor
                if (mFilter != null) {
                    // Have we seen this name before?
                    Boolean allowedState = mFilterMap.get(name);
                    if (allowedState == null) {
                        // New class -- remember whether it is allowed
                        clazz = mContext.getClassLoader().loadClass(
                                prefix != null ? (prefix + name) : name).asSubclass(View.class);

                        boolean allowed = clazz != null && mFilter.onLoadClass(clazz);
                        mFilterMap.put(name, allowed);
                        if (!allowed) {
                            failNotAllowed(name, prefix, attrs);
                        }
                    } else if (allowedState.equals(Boolean.FALSE)) {
                        failNotAllowed(name, prefix, attrs);
                    }
                }
            }

            Object lastContext = mConstructorArgs[0];
            if (mConstructorArgs[0] == null) {
                // Fill in the context if not already within inflation.
                mConstructorArgs[0] = mContext;
            }
            Object[] args = mConstructorArgs;
            args[1] = attrs;
			// 调用构造函数，生成View类，args是两个参数的构造方法，所以也就是我们xml中写的布局，其实最后都会调用两个参数的那个构造方法
            final View view = constructor.newInstance(args);
            if (view instanceof ViewStub) {
                // Use the same context when inflating ViewStub later.
                // 如果是ViewStub类
                final ViewStub viewStub = (ViewStub) view;
                viewStub.setLayoutInflater(cloneInContext((Context) args[0]));
            }
            mConstructorArgs[0] = lastContext;
            return view;

        } catch (NoSuchMethodException e) {
            final InflateException ie = new InflateException(attrs.getPositionDescription()
                    + ": Error inflating class " + (prefix != null ? (prefix + name) : name), e);
            ie.setStackTrace(EMPTY_STACK_TRACE);
            throw ie;

        } catch (ClassCastException e) {
            // If loaded class is not a View subclass
            final InflateException ie = new InflateException(attrs.getPositionDescription()
                    + ": Class is not a View " + (prefix != null ? (prefix + name) : name), e);
            ie.setStackTrace(EMPTY_STACK_TRACE);
            throw ie;
        } catch (ClassNotFoundException e) {
            // If loadClass fails, we should propagate the exception.
            throw e;
        } catch (Exception e) {
            final InflateException ie = new InflateException(
                    attrs.getPositionDescription() + ": Error inflating class "
                            + (clazz == null ? "<unknown>" : clazz.getName()), e);
            ie.setStackTrace(EMPTY_STACK_TRACE);
            throw ie;
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_VIEW);
        }
    }

    /**
     * Throw an exception because the specified class is not allowed to be inflated.
     * 引发异常，因为指定的类不允许膨胀。
     */
    private void failNotAllowed(String name, String prefix, AttributeSet attrs) {
        throw new InflateException(attrs.getPositionDescription()
                + ": Class not allowed to be inflated "+ (prefix != null ? (prefix + name) : name));
    }

    /**
     * This routine is responsible for creating the correct subclass of View
     * given the xml element name. Override it to handle custom view objects. If
     * you override this in your subclass be sure to call through to
     * super.onCreateView(name) for names you do not recognize.
     *
     * @param name The fully qualified class name of the View to be create.
     * @param attrs An AttributeSet of attributes to apply to the View.
     *
     * @return View The View created.
     */
    protected View onCreateView(String name, AttributeSet attrs)
            throws ClassNotFoundException {
        return createView(name, "android.view.", attrs);
    }

    /**
     * Version of {@link #onCreateView(String, AttributeSet)} that also
     * takes the future parent of the view being constructed.  The default
     * implementation simply calls {@link #onCreateView(String, AttributeSet)}.
     *
     * @param parent The future parent of the returned view.  <em>Note that
     * this may be null.</em>
     * @param name The fully qualified class name of the View to be create.
     * @param attrs An AttributeSet of attributes to apply to the View.
     *
     * @return View The View created.
     */
    protected View onCreateView(View parent, String name, AttributeSet attrs)
            throws ClassNotFoundException {
        return onCreateView(name, attrs);
    }

    /**
     * Convenience method for calling through to the five-arg createViewFromTag
     * method. This method passes {@code false} for the {@code ignoreThemeAttr}
     * argument and should be used for everything except {@code &gt;include>}
     * tag parsing.
     */
    private View createViewFromTag(View parent, String name, Context context, AttributeSet attrs) {
        return createViewFromTag(parent, name, context, attrs, false);
    }

    /**
     * Creates a view from a tag name using the supplied attribute set.
     * <p>
     * <strong>Note:</strong> Default visibility so the BridgeInflater can
     * override it.
     *
     * @param parent the parent view, used to inflate layout params
     * @param name the name of the XML tag used to define the view
     * @param context the inflation context for the view, typically the
     *                {@code parent} or base layout inflater context
     * @param attrs the attribute set for the XML tag used to define the view
     * @param ignoreThemeAttr {@code true} to ignore the {@code android:theme}
     *                        attribute (if set) for the view being inflated,
     *                        {@code false} otherwise
     */
    View createViewFromTag(View parent, String name, Context context, AttributeSet attrs,
            boolean ignoreThemeAttr) {
        if (name.equals("view")) {
            name = attrs.getAttributeValue(null, "class");
        }

        // Apply a theme wrapper, if allowed and one is specified.
        // 进行主题的处理
        if (!ignoreThemeAttr) {
            final TypedArray ta = context.obtainStyledAttributes(attrs, ATTRS_THEME);
            final int themeResId = ta.getResourceId(0, 0);
            if (themeResId != 0) {
                context = new ContextThemeWrapper(context, themeResId);
            }
			// 资源回收
            ta.recycle();
        }

        if (name.equals(TAG_1995)) {
            // Let's party like it's 1995!
            return new BlinkLayout(context, attrs);
        }

        try {
			// 尝试通过Factory来创建View。这里是个hook的点，如果没有设置的话，会返回 null
            View view;
			// HOOK点 根据设置的各种Factory，通过其 onCreateView() 方法进行View的创建。
            if (mFactory2 != null) {
                view = mFactory2.onCreateView(parent, name, context, attrs);
            } else if (mFactory != null) {
                view = mFactory.onCreateView(name, context, attrs);
            } else {
                view = null;
            }

            if (view == null && mPrivateFactory != null) {
                view = mPrivateFactory.onCreateView(parent, name, context, attrs);
            }

            if (view == null) {
				// 如果创建的view为空，那么就尝试从系统的控件中加载
                final Object lastContext = mConstructorArgs[0];
                mConstructorArgs[0] = context;
                try {
					/**
					 * 根据name是否包含“.”来判断是否是自定义view。一般自定义的View是包含.的，
                     * 其实最终都会调用createView方法。系统自带的View，会将prefix设置为“android.view”，然后调用createView方法
					 */
                    if (-1 == name. ('.')) {
                        view = onCreateView(parent, name, attrs);
                    } else {
                        view = createView(name, null, attrs);
                    }
                } finally {
                    mConstructorArgs[0] = lastContext;
                }
            }

            return view;
        } catch (InflateException e) {
            throw e;

        } catch (ClassNotFoundException e) {
            final InflateException ie = new InflateException(attrs.getPositionDescription()
                    + ": Error inflating class " + name, e);
            ie.setStackTrace(EMPTY_STACK_TRACE);
            throw ie;

        } catch (Exception e) {
            final InflateException ie = new InflateException(attrs.getPositionDescription()
                    + ": Error inflating class " + name, e);
            ie.setStackTrace(EMPTY_STACK_TRACE);
            throw ie;
        }
    }

    /**
     * Recursive method used to inflate internal (non-root) children. This
     * method calls through to {@link #rInflate} using the parent context as
     * the inflation context.
     * <strong>Note:</strong> Default visibility so the BridgeInflater can
     * call it.
     */
    final void rInflateChildren(XmlPullParser parser, View parent, AttributeSet attrs,
            boolean finishInflate) throws XmlPullParserException, IOException {
        rInflate(parser, parent, parent.getContext(), attrs, finishInflate);
    }

    /**
     * Recursive method used to descend down the xml hierarchy and instantiate
     * views, instantiate their children, and then call onFinishInflate().
     * <p>
     * <strong>Note:</strong> Default visibility so the BridgeInflater can
     * override it.
     * 
     * 
     * 递归方法，用于向下传递xml层次结构并实例化视图、实例化其子视图，然后调用onfinishinfl()
     */
    void rInflate(XmlPullParser parser, View parent, Context context,
            AttributeSet attrs, boolean finishInflate) throws XmlPullParserException, IOException {

        final int depth = parser.getDepth();
        int type;
        boolean pendingRequestFocus = false;

        while (((type = parser.next()) != XmlPullParser.END_TAG ||
                parser.getDepth() > depth) && type != XmlPullParser.END_DOCUMENT) {

            if (type != XmlPullParser.START_TAG) {
                continue;
            }

			// 获取标签名
            final String name = parser.getName();

            if (TAG_REQUEST_FOCUS.equals(name)) {
                pendingRequestFocus = true;
                consumeChildElements(parser);
            } else if (TAG_TAG.equals(name)) {

				// tag标签
                parseViewTag(parser, parent, attrs);
            } else if (TAG_INCLUDE.equals(name)) {
				// include标签不能是根标签
                if (parser.getDepth() == 0) {
					// merge标签必须是布局的根元素
                    throw new InflateException("<include /> cannot be the root element");
                }
                parseInclude(parser, context, parent, attrs);
            } else if (TAG_MERGE.equals(name)) {
                throw new InflateException("<merge /> must be the root element");
            } else {
				// 调用createViewFromTag方法，生成对应的view，然后将子view添加到parent布局中
                final View view = createViewFromTag(parent, name, context, attrs);
                final ViewGroup viewGroup = (ViewGroup) parent;
                final ViewGroup.LayoutParams params = viewGroup.generateLayoutParams(attrs);
				// 遍历view下面的子控件
                rInflateChildren(parser, view, attrs, true);
                viewGroup.addView(view, params);
            }
        }

        if (pendingRequestFocus) {
            parent.restoreDefaultFocus();
        }

        if (finishInflate) {
            parent.onFinishInflate();
        }
    }

    /**
     * Parses a <code>&lt;tag&gt;</code> element and sets a keyed tag on the
     * containing View.
     */
    private void parseViewTag(XmlPullParser parser, View view, AttributeSet attrs)
            throws XmlPullParserException, IOException {
        final Context context = view.getContext();
        final TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.ViewTag);
        final int key = ta.getResourceId(R.styleable.ViewTag_id, 0);
        final CharSequence value = ta.getText(R.styleable.ViewTag_value);
        view.setTag(key, value);
        ta.recycle();

        consumeChildElements(parser);
    }

    private void parseInclude(XmlPullParser parser, Context context, View parent,
            AttributeSet attrs) throws XmlPullParserException, IOException {
        int type;

        if (parent instanceof ViewGroup) {
            // Apply a theme wrapper, if requested. This is sort of a weird
            // edge case, since developers think the <include> overwrites
            // values in the AttributeSet of the included View. So, if the
            // included View has a theme attribute, we'll need to ignore it.
            final TypedArray ta = context.obtainStyledAttributes(attrs, ATTRS_THEME);
            final int themeResId = ta.getResourceId(0, 0);
            final boolean hasThemeOverride = themeResId != 0;
            if (hasThemeOverride) {
                context = new ContextThemeWrapper(context, themeResId);
            }
            ta.recycle();

            // If the layout is pointing to a theme attribute, we have to
            // massage the value to get a resource identifier out of it.
            int layout = attrs.getAttributeResourceValue(null, ATTR_LAYOUT, 0);
            if (layout == 0) {
                final String value = attrs.getAttributeValue(null, ATTR_LAYOUT);
                if (value == null || value.length() <= 0) {
                    throw new InflateException("You must specify a layout in the"
                            + " include tag: <include layout=\"@layout/layoutID\" />");
                }

                // Attempt to resolve the "?attr/name" string to an attribute
                // within the default (e.g. application) package.
                layout = context.getResources().getIdentifier(
                        value.substring(1), "attr", context.getPackageName());

            }

            // The layout might be referencing a theme attribute.
            if (mTempValue == null) {
                mTempValue = new TypedValue();
            }
            if (layout != 0 && context.getTheme().resolveAttribute(layout, mTempValue, true)) {
                layout = mTempValue.resourceId;
            }

            if (layout == 0) {
                final String value = attrs.getAttributeValue(null, ATTR_LAYOUT);
                throw new InflateException("You must specify a valid layout "
                        + "reference. The layout ID " + value + " is not valid.");
            } else {
                final XmlResourceParser childParser = context.getResources().getLayout(layout);

                try {
                    final AttributeSet childAttrs = Xml.asAttributeSet(childParser);

                    while ((type = childParser.next()) != XmlPullParser.START_TAG &&
                            type != XmlPullParser.END_DOCUMENT) {
                        // Empty.
                    }

                    if (type != XmlPullParser.START_TAG) {
                        throw new InflateException(childParser.getPositionDescription() +
                                ": No start tag found!");
                    }

                    final String childName = childParser.getName();

                    if (TAG_MERGE.equals(childName)) {
                        // The <merge> tag doesn't support android:theme, so
                        // nothing special to do here.
                        rInflate(childParser, parent, context, childAttrs, false);
                    } else {
                        final View view = createViewFromTag(parent, childName,
                                context, childAttrs, hasThemeOverride);
                        final ViewGroup group = (ViewGroup) parent;

                        final TypedArray a = context.obtainStyledAttributes(
                                attrs, R.styleable.Include);
                        final int id = a.getResourceId(R.styleable.Include_id, View.NO_ID);
                        final int visibility = a.getInt(R.styleable.Include_visibility, -1);
                        a.recycle();

                        // We try to load the layout params set in the <include /> tag.
                        // If the parent can't generate layout params (ex. missing width
                        // or height for the framework ViewGroups, though this is not
                        // necessarily true of all ViewGroups) then we expect it to throw
                        // a runtime exception.
                        // We catch this exception and set localParams accordingly: true
                        // means we successfully loaded layout params from the <include>
                        // tag, false means we need to rely on the included layout params.
                        ViewGroup.LayoutParams params = null;
                        try {
                            params = group.generateLayoutParams(attrs);
                        } catch (RuntimeException e) {
                            // Ignore, just fail over to child attrs.
                        }
                        if (params == null) {
                            params = group.generateLayoutParams(childAttrs);
                        }
                        view.setLayoutParams(params);

                        // Inflate all children.
                        rInflateChildren(childParser, view, childAttrs, true);

                        if (id != View.NO_ID) {
                            view.setId(id);
                        }

                        switch (visibility) {
                            case 0:
                                view.setVisibility(View.VISIBLE);
                                break;
                            case 1:
                                view.setVisibility(View.INVISIBLE);
                                break;
                            case 2:
                                view.setVisibility(View.GONE);
                                break;
                        }

                        group.addView(view);
                    }
                } finally {
                    childParser.close();
                }
            }
        } else {
            throw new InflateException("<include /> can only be used inside of a ViewGroup");
        }

        LayoutInflater.consumeChildElements(parser);
    }

    /**
     * <strong>Note:</strong> default visibility so that
     * LayoutInflater_Delegate can call it.
     */
    final static void consumeChildElements(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        int type;
        final int currentDepth = parser.getDepth();
        while (((type = parser.next()) != XmlPullParser.END_TAG ||
                parser.getDepth() > currentDepth) && type != XmlPullParser.END_DOCUMENT) {
            // Empty
        }
    }

    private static class BlinkLayout extends FrameLayout {
        private static final int MESSAGE_BLINK = 0x42;
        private static final int BLINK_DELAY = 500;

        private boolean mBlink;
        private boolean mBlinkState;
        private final Handler mHandler;

        public BlinkLayout(Context context, AttributeSet attrs) {
            super(context, attrs);
            mHandler = new Handler(new Handler.Callback() {
                @Override
                public boolean handleMessage(Message msg) {
                    if (msg.what == MESSAGE_BLINK) {
                        if (mBlink) {
                            mBlinkState = !mBlinkState;
                            makeBlink();
                        }
                        invalidate();
                        return true;
                    }
                    return false;
                }
            });
        }

        private void makeBlink() {
            Message message = mHandler.obtainMessage(MESSAGE_BLINK);
            mHandler.sendMessageDelayed(message, BLINK_DELAY);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();

            mBlink = true;
            mBlinkState = true;

            makeBlink();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();

            mBlink = false;
            mBlinkState = true;

            mHandler.removeMessages(MESSAGE_BLINK);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (mBlinkState) {
                super.dispatchDraw(canvas);
            }
        }
    }
}
