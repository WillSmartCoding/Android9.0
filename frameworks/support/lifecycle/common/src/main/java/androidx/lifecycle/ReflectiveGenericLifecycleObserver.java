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

import androidx.lifecycle.ClassesInfoCache.CallbackInfo;
import androidx.lifecycle.Lifecycle.Event;

/**
 * An internal implementation of {@link GenericLifecycleObserver} that relies on reflection.
 */
class ReflectiveGenericLifecycleObserver implements GenericLifecycleObserver {
    private final Object mWrapped;
    private final CallbackInfo mInfo;

	/**
	 * Object wrapped 就是传入的 LifecycleObserver
	 *
	 */
    ReflectiveGenericLifecycleObserver(Object wrapped) {
        mWrapped = wrapped;、
		// 缓存Observer 的信息并返回赋值 mInfo
        mInfo = ClassesInfoCache.sInstance.getInfo(mWrapped.getClass());
    }

    @Override
    public void onStateChanged(LifecycleOwner source, Event event) {
        mInfo.invokeCallbacks(source, event, mWrapped);
    }
}
