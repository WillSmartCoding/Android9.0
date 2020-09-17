/*
 * Copyright (C) 2009 The Android Open Source Project
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

import java.util.Map;

public final class ServiceManager {

    /**
     * Returns a reference to a service with the given name.
     * 返回对具有给定名称的服务的引用，如果服务不存在则返回null。
     *
     * @param name the name of the service to get
     * @return a reference to the service, or <code>null</code> if the service doesn't exist
     */
    public static IBinder getService(String name) {
        return null;
    }

    /**
     * Is not supposed to return null, but that is fine for layoutlib.
     * 不应返回null，但这对于layoutlib来说是可以的。
     */
    public static IBinder getServiceOrThrow(String name) throws ServiceNotFoundException {
        throw new ServiceNotFoundException(name);
    }

    /**
     * Place a new @a service called @a name into the service
     * manager.
     * 添加一个新的服务到 ServiceManager
     *
     * @param name 新服务的名称
     * @param service the service object
     */
    public static void addService(String name, IBinder service) {
        // pass
    }

    /**
     * Retrieve an existing service called @a name from the
     * service manager.  Non-blocking.
     * 在ServiceManager中检索符合name的Service
     */
    public static IBinder checkService(String name) {
        return null;
    }

    /**
     * Return a list of all currently running services.
     * 返回当前正在运行的所有services的列表。
     * @return an array of all currently running services, or <code>null</code> in
     * case of an exception
     */
    public static String[] listServices() {
        // actual implementation returns null sometimes, so it's ok
        // to return null instead of an empty list.
        return null;
    }

    /**
     * This is only intended to be called when the process is first being brought
     * up and bound by the activity manager. There is only one thread in the process
     * at that time, so no locking is done.
     * 
     * 只有当进程第一次被ActivityManager启动并绑定时，才需要调用它。此时进程中只有一个线程，因此没有进行锁定。
     * @param cache the cache of service references
     * @hide
     */
    public static void initServiceCache(Map<String, IBinder> cache) {
        // pass
    }

    /**
     * Exception thrown when no service published for given name. This might be
     * thrown early during boot before certain services have published
     * themselves.
     * 当没有为给定名称发布service时引发异常。这可能会在某些service发布自己之前在引导期间抛出。
     *
     * @hide
     */
    public static class ServiceNotFoundException extends Exception {
        // identical to the original implementation
        public ServiceNotFoundException(String name) {
            super("No service published for: " + name);
        }
    }
}
