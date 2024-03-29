/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.logging.log4j.util;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.function.Supplier;
import org.apache.logging.log4j.spi.LoggingSystemProperty;

/**
 * <em>Consider this class private.</em> Utility class for ClassLoaders.
 *
 * @see ClassLoader
 * @see RuntimePermission
 * @see Thread#getContextClassLoader()
 * @see ClassLoader#getSystemClassLoader()
 */
@InternalApi
public final class LoaderUtil {

    private static final ClassLoader[] EMPTY_CLASS_LOADER_ARRAY = {};

    // this variable must be lazily loaded; otherwise, we get a nice circular class loading problem where LoaderUtil
    // wants to use PropertiesUtil, but then PropertiesUtil wants to use LoaderUtil.
    private static Boolean ignoreTCCL;
    static Boolean forceTcclOnly;

    static {
        if (System.getSecurityManager() != null) {
            LowLevelLogUtil.log(
                    "A custom SecurityManager was detected; Log4j no longer supports security permissions.");
        }
    }

    private LoaderUtil() {}

    /**
     * Returns the ClassLoader to use.
     *
     * @return the ClassLoader.
     */
    public static ClassLoader getClassLoader() {
        return getClassLoader(LoaderUtil.class, null);
    }

    // TODO: this method could use some explanation
    public static ClassLoader getClassLoader(final Class<?> class1, final Class<?> class2) {
        final ClassLoader loader1 = class1 == null ? null : class1.getClassLoader();
        final ClassLoader loader2 = class2 == null ? null : class2.getClassLoader();
        final ClassLoader referenceLoader = Thread.currentThread().getContextClassLoader();
        if (isChild(referenceLoader, loader1)) {
            return isChild(referenceLoader, loader2) ? referenceLoader : loader2;
        }
        return isChild(loader1, loader2) ? loader1 : loader2;
    }

    /**
     * Determines if one ClassLoader is a child of another ClassLoader. Note that a {@code null} ClassLoader is
     * interpreted as the system ClassLoader as per convention.
     *
     * @param loader1 the ClassLoader to check for childhood.
     * @param loader2 the ClassLoader to check for parenthood.
     * @return {@code true} if the first ClassLoader is a strict descendant of the second ClassLoader.
     */
    private static boolean isChild(final ClassLoader loader1, final ClassLoader loader2) {
        if (loader1 != null && loader2 != null) {
            ClassLoader parent = loader1.getParent();
            while (parent != null && parent != loader2) {
                parent = parent.getParent();
            }
            // once parent is null, we're at the system CL, which would indicate they have separate ancestry
            return parent != null;
        }
        return loader1 != null;
    }

    /**
     * Looks up the ClassLoader for this current thread falling back to either the
     * ClassLoader of this class or the {@linkplain ClassLoader#getSystemClassLoader() system ClassLoader}.
     * If none of these strategies can obtain a ClassLoader, then this returns {@code null}.
     *
     * @return the current thread's ClassLoader, a fallback loader, or null if no fallback can be determined
     */
    public static ClassLoader getThreadContextClassLoader() {
        final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null) {
            return contextClassLoader;
        }
        final ClassLoader thisClassLoader = getThisClassLoader();
        if (thisClassLoader != null) {
            return thisClassLoader;
        }
        return ClassLoader.getSystemClassLoader();
    }

    public static ClassLoader[] getClassLoaders() {
        final Collection<ClassLoader> classLoaders = new LinkedHashSet<>();
        final ClassLoader tcl = getThreadContextClassLoader();
        if (tcl != null) {
            classLoaders.add(tcl);
        }
        final ModuleLayer layer = LoaderUtil.class.getModule().getLayer();
        if (layer == null) {
            if (!isForceTccl()) {
                accumulateClassLoaders(getThisClassLoader(), classLoaders);
                accumulateClassLoaders(tcl == null ? null : tcl.getParent(), classLoaders);
                final ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
                if (systemClassLoader != null) {
                    classLoaders.add(systemClassLoader);
                }
            }
        } else {
            accumulateLayerClassLoaders(layer, classLoaders);
            if (layer != ModuleLayer.boot()) {
                for (final Module module : ModuleLayer.boot().modules()) {
                    accumulateClassLoaders(module.getClassLoader(), classLoaders);
                }
            }
        }
        return classLoaders.toArray(EMPTY_CLASS_LOADER_ARRAY);
    }

    private static ClassLoader getThisClassLoader() {
        return LoaderUtil.class.getClassLoader();
    }

    private static void accumulateLayerClassLoaders(
            final ModuleLayer layer, final Collection<ClassLoader> classLoaders) {
        for (final Module module : layer.modules()) {
            accumulateClassLoaders(module.getClassLoader(), classLoaders);
        }
        if (!layer.parents().isEmpty()) {
            for (final ModuleLayer parent : layer.parents()) {
                accumulateLayerClassLoaders(parent, classLoaders);
            }
        }
    }

    /**
     * Adds the provided loader to the loaders collection, and traverses up the tree until either a null
     * value or a classloader which has already been added is encountered.
     */
    private static void accumulateClassLoaders(final ClassLoader loader, final Collection<ClassLoader> loaders) {
        // Some implementations may use null to represent the bootstrap class loader.
        if (loader != null && loaders.add(loader)) {
            accumulateClassLoaders(loader.getParent(), loaders);
        }
    }

    /**
     * Determines if a named Class can be loaded or not.
     *
     * @param className The class name.
     * @return {@code true} if the class could be found or {@code false} otherwise.
     * @since 2.7
     */
    public static boolean isClassAvailable(final String className) {
        try {
            loadClass(className);
            return true;
        } catch (final ClassNotFoundException | LinkageError e) {
            return false;
        } catch (final Throwable e) {
            LowLevelLogUtil.logException("Unknown error checking for existence of class: " + className, e);
            return false;
        }
    }

    /**
     * Loads and initializes a class given its fully qualified class name. This method respects the
     * {@link LoggingSystemProperty#LOADER_IGNORE_THREAD_CONTEXT_LOADER} Log4j property. If this property is specified
     * and set to anything besides {@code false}, then this class's ClassLoader will be used.
     *
     * @param className fully qualified class name to load
     * @return the loaded class
     * @throws ClassNotFoundException      if the specified class name could not be found
     * @throws ExceptionInInitializerError if an exception is thrown during class initialization
     * @throws LinkageError                if the linkage of the class fails for any other reason
     * @since 2.1
     */
    public static Class<?> loadClass(final String className) throws ClassNotFoundException {
        ClassLoader classLoader = isIgnoreTccl() ? getThisClassLoader() : getThreadContextClassLoader();
        if (classLoader == null) {
            classLoader = getThisClassLoader();
        }
        return Class.forName(className, true, classLoader);
    }

    /**
     * Loads and initializes a class given its fully qualified class name. All checked reflective operation
     * exceptions are translated into equivalent {@link LinkageError} classes.
     *
     * @param className fully qualified class name to load
     * @return the loaded class
     * @throws NoClassDefFoundError        if the specified class name could not be found
     * @throws ExceptionInInitializerError if an exception is thrown during class initialization
     * @throws LinkageError                if the linkage of the class fails for any other reason
     * @see #loadClass(String)
     * @since 2.22.0
     */
    public static Class<?> loadClassUnchecked(final String className) {
        try {
            return loadClass(className);
        } catch (final ClassNotFoundException e) {
            final NoClassDefFoundError error = new NoClassDefFoundError(e.getMessage());
            error.initCause(e);
            throw error;
        }
    }

    /**
     * Loads and instantiates a Class using the default constructor.
     *
     * @param <T>   the type of the class modeled by the {@code Class} object.
     * @param clazz The class.
     * @return new instance of the class.
     * @throws NoSuchMethodException       if no zero-arg constructor exists
     * @throws SecurityException           if this class is not allowed to access declared members of the provided class
     * @throws IllegalAccessException      if the class can't be instantiated through a public constructor
     * @throws InstantiationException      if the provided class is abstract or an interface
     * @throws InvocationTargetException   if an exception is thrown by the constructor
     * @throws ExceptionInInitializerError if an exception was thrown while initializing the class
     * @since 2.7
     */
    public static <T> T newInstanceOf(final Class<T> clazz)
            throws InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        final Constructor<T> constructor = clazz.getDeclaredConstructor();
        return constructor.newInstance();
    }

    /**
     * Creates an instance of the provided class using the default constructor. All checked reflective operation
     * exceptions are translated into {@link LinkageError} or {@link InternalException}.
     *
     * @param clazz class to instantiate
     * @param <T>   the type of the object being instantiated
     * @return instance of the class
     * @throws NoSuchMethodError  if no zero-arg constructor exists
     * @throws SecurityException  if this class is not allowed to access declared members of the provided class
     * @throws InternalException  if an exception is thrown by the constructor
     * @throws InstantiationError if the provided class is abstract or an interface
     * @throws IllegalAccessError if the class cannot be accessed
     * @since 2.22.0
     */
    public static <T> T newInstanceOfUnchecked(final Class<T> clazz) {
        try {
            return newInstanceOf(clazz);
        } catch (final NoSuchMethodException e) {
            final NoSuchMethodError error = new NoSuchMethodError(e.getMessage());
            error.initCause(e);
            throw error;
        } catch (final InvocationTargetException e) {
            final Throwable cause = e.getCause();
            throw new InternalException(cause);
        } catch (final InstantiationException e) {
            final InstantiationError error = new InstantiationError(e.getMessage());
            error.initCause(e);
            throw error;
        } catch (final IllegalAccessException e) {
            final IllegalAccessError error = new IllegalAccessError(e.getMessage());
            error.initCause(e);
            throw error;
        }
    }

    /**
     * Loads and instantiates a Class using the default constructor.
     *
     * @param className fully qualified class name to load, initialize, and construct
     * @param <T>       type the class must be compatible with
     * @return new instance of the class
     * @throws ClassNotFoundException      if the class isn't available to the usual ClassLoaders
     * @throws ExceptionInInitializerError if an exception was thrown while initializing the class
     * @throws LinkageError                if the linkage of the class fails for any other reason
     * @throws ClassCastException          if the class is not compatible with the generic type parameter provided
     * @throws NoSuchMethodException       if no zero-arg constructor exists
     * @throws SecurityException           if this class is not allowed to access declared members of the provided class
     * @throws IllegalAccessException      if the class can't be instantiated through a public constructor
     * @throws InstantiationException      if the provided class is abstract or an interface
     * @throws InvocationTargetException   if an exception is thrown by the constructor
     * @since 2.1
     */
    public static <T> T newInstanceOf(final String className)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException, InvocationTargetException,
                    NoSuchMethodException {
        final Class<T> clazz = Cast.cast(loadClass(className));
        return newInstanceOf(clazz);
    }

    /**
     * Loads and instantiates a class given by a property name.
     *
     * @param propertyKey The property name to look up a class name for.
     * @param clazz        The class to cast it to.
     * @param defaultSupplier Supplier of a default value if the property is not present.
     * @param <T>          The type to cast it to.
     * @return new instance of the class given in the property or {@code null} if the property was unset.
     * @throws ClassNotFoundException      if the class isn't available to the usual ClassLoaders
     * @throws ExceptionInInitializerError if an exception was thrown while initializing the class
     * @throws LinkageError                if the linkage of the class fails for any other reason
     * @throws ClassCastException          if the class is not compatible with the generic type parameter provided
     * @throws NoSuchMethodException       if no zero-arg constructor exists
     * @throws SecurityException           if this class is not allowed to access declared members of the provided class
     * @throws IllegalAccessException      if the class can't be instantiated through a public constructor
     * @throws InstantiationException      if the provided class is abstract or an interface
     * @throws InvocationTargetException   if an exception is thrown by the constructor
     * @since 3.0.0
     */
    public static <T> T newCheckedInstanceOfProperty(
            final PropertyKey propertyKey, final Class<T> clazz, final Supplier<T> defaultSupplier)
            throws ReflectiveOperationException {
        final String className = PropertiesUtil.getProperties().getStringProperty(propertyKey);
        if (className == null) {
            return defaultSupplier.get();
        }
        return newCheckedInstanceOf(className, clazz);
    }

    /**
     * Loads and instantiates a class by name using its default constructor. All checked reflective operation
     * exceptions are translated into corresponding {@link LinkageError} classes.
     *
     * @param className fully qualified class name to load, initialize, and construct
     * @param <T>       type the class must be compatible with
     * @return new instance of the class
     * @throws NoClassDefFoundError        if the specified class name could not be found
     * @throws ExceptionInInitializerError if an exception is thrown during class initialization
     * @throws ClassCastException          if the class is not compatible with the generic type parameter provided
     * @throws NoSuchMethodError           if no zero-arg constructor exists
     * @throws SecurityException           if this class is not allowed to access declared members of the provided class
     * @throws InternalException           if an exception is thrown by the constructor
     * @throws InstantiationError          if the provided class is abstract or an interface
     * @throws IllegalAccessError          if the class cannot be accessed
     * @throws LinkageError                if the linkage of the class fails for any other reason
     * @since 2.22.0
     */
    public static <T> T newInstanceOfUnchecked(final String className) {
        final Class<T> clazz = Cast.cast(loadClassUnchecked(className));
        return newInstanceOfUnchecked(clazz);
    }

    /**
     * Loads and instantiates a derived class using its default constructor.
     *
     * @param className The class name.
     * @param clazz     The class to cast it to.
     * @param <T>       The type of the class to check.
     * @return new instance of the class cast to {@code T}
     * @throws ClassNotFoundException      if the class isn't available to the usual ClassLoaders
     * @throws ExceptionInInitializerError if an exception is thrown during class initialization
     * @throws LinkageError                if the linkage of the class fails for any other reason
     * @throws ClassCastException          if the constructed object isn't type compatible with {@code T}
     * @throws NoSuchMethodException       if no zero-arg constructor exists
     * @throws SecurityException           if this class is not allowed to access declared members of the provided class
     * @throws IllegalAccessException      if the class can't be instantiated through a public constructor
     * @throws InstantiationException      if the provided class is abstract or an interface
     * @throws InvocationTargetException   if there was an exception whilst constructing the class
     * @since 2.1
     */
    public static <T> T newCheckedInstanceOf(final String className, final Class<T> clazz)
            throws ClassNotFoundException, InvocationTargetException, InstantiationException, IllegalAccessException,
                    NoSuchMethodException {
        return newInstanceOf(loadClass(className).asSubclass(clazz));
    }

    /**
     * Loads the provided class by name as a checked subtype of the given class. All checked reflective operation
     * exceptions are translated into corresponding {@link LinkageError} classes.
     *
     * @param className fully qualified class name to load
     * @param supertype supertype of the class being loaded
     * @param <T>       type of instance to return
     * @return new instance of the requested class
     * @throws NoClassDefFoundError        if the provided class name could not be found
     * @throws ExceptionInInitializerError if an exception is thrown during class initialization
     * @throws ClassCastException          if the loaded class is not a subtype of the provided class
     * @throws NoSuchMethodError           if no zero-arg constructor exists
     * @throws SecurityException           if this class is not allowed to access declared members of the provided class
     * @throws InternalException           if an exception is thrown by the constructor
     * @throws InstantiationError          if the provided class is abstract or an interface
     * @throws IllegalAccessError          if the class cannot be accessed
     * @throws LinkageError                if the linkage of the class fails for any other reason
     * @since 2.22.0
     */
    public static <T> T newInstanceOfUnchecked(final String className, final Class<T> supertype) {
        final Class<? extends T> clazz = loadClassUnchecked(className).asSubclass(supertype);
        return newInstanceOfUnchecked(clazz);
    }

    private static boolean isIgnoreTccl() {
        // we need to lazily initialize this, but concurrent access is not an issue
        if (ignoreTCCL == null) {
            final String ignoreTccl = PropertiesUtil.getProperties()
                    .getStringProperty(LoggingSystemProperty.LOADER_IGNORE_THREAD_CONTEXT_LOADER, null);
            ignoreTCCL = ignoreTccl != null && !"false".equalsIgnoreCase(ignoreTccl.trim());
        }
        return ignoreTCCL;
    }

    private static boolean isForceTccl() {
        if (forceTcclOnly == null) {
            // PropertiesUtil.getProperties() uses that code path so don't use that!
            forceTcclOnly = Boolean.getBoolean(LoggingSystemProperty.LOADER_FORCE_THREAD_CONTEXT_LOADER.getSystemKey());
        }
        return forceTcclOnly;
    }

    /**
     * Finds classpath {@linkplain URL resources}.
     *
     * @param resource the name of the resource to find.
     * @return a Collection of URLs matching the resource name. If no resources could be found, then this will be empty.
     * @since 2.1
     */
    public static Collection<URL> findResources(final String resource) {
        final Collection<UrlResource> urlResources = findUrlResources(resource);
        final Collection<URL> resources = new LinkedHashSet<>(urlResources.size());
        for (final UrlResource urlResource : urlResources) {
            resources.add(urlResource.getUrl());
        }
        return resources;
    }

    /**
     * This method will only find resources that follow the JPMS rules for encapsulation. Resources
     * on the class path should be found as normal along with resources with no package name in all
     * modules. Resources within packages in modules must declare those resources open to org.apache.logging.log4j.
     *
     * @param resource The resource to locate.
     * @return The located resources.
     */
    public static Collection<UrlResource> findUrlResources(final String resource) {
        final Collection<UrlResource> resources = new LinkedHashSet<>();
        for (final ClassLoader cl : getClassLoaders()) {
            if (cl != null) {
                try {
                    final Enumeration<URL> resourceEnum = cl.getResources(resource);
                    while (resourceEnum.hasMoreElements()) {
                        resources.add(new UrlResource(cl, resourceEnum.nextElement()));
                    }
                } catch (final IOException e) {
                    LowLevelLogUtil.logException(e);
                }
            }
        }
        return resources;
    }

    /**
     * {@link URL} and {@link ClassLoader} pair.
     */
    public static class UrlResource {
        private final ClassLoader classLoader;
        private final URL url;

        public UrlResource(final ClassLoader classLoader, final URL url) {
            this.classLoader = classLoader;
            this.url = url;
        }

        public ClassLoader getClassLoader() {
            return classLoader;
        }

        public URL getUrl() {
            return url;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof UrlResource)) {
                return false;
            }

            final UrlResource that = (UrlResource) o;

            return Objects.equals(classLoader, that.classLoader) && Objects.equals(url, that.url);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(classLoader) + Objects.hashCode(url);
        }
    }
}
