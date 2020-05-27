package org.apache.maven.plugins.dependency.tree.internal;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.plugins.dependency.tree.DependencyGraphBuilderException;

import java.lang.reflect.InvocationTargetException;

/**
 * Invokes method on objects using reflection.
 */
final class Invoker
{
    private Invoker()
    {
        // do not instantiate
    }

    public static Object invoke( Object object, String method )
        throws DependencyGraphBuilderException
    {
        return invoke( object.getClass(), object, method );
    }

    public static Object invoke( Class<?> objectClazz, Object object, String method )
        throws DependencyGraphBuilderException
    {
        try
        {
            return objectClazz.getMethod( method ).invoke( object );
        }
        catch ( IllegalAccessException e )
        {
            throw new DependencyGraphBuilderException( e.getMessage(), e );
        }
        catch ( InvocationTargetException e )
        {
            throw new DependencyGraphBuilderException( e.getMessage(), e );
        }
        catch ( NoSuchMethodException e )
        {
            throw new DependencyGraphBuilderException( e.getMessage(), e );
        }
    }

    public static Object invoke( Object object, String method, Class<?> clazz, Object arg )
        throws DependencyGraphBuilderException
    {
        try
        {
            final Class<?> objectClazz = object.getClass();
            return objectClazz.getMethod( method, clazz ).invoke( object, arg );
        }
        catch ( IllegalAccessException e )
        {
            throw new DependencyGraphBuilderException( e.getMessage(), e );
        }
        catch ( InvocationTargetException e )
        {
            throw new DependencyGraphBuilderException( e.getMessage(), e );
        }
        catch ( NoSuchMethodException e )
        {
            throw new DependencyGraphBuilderException( e.getMessage(), e );
        }
    }
    
    public static Object invoke( Class<?> objectClazz, String staticMethod, Class<?> argClazz, Object arg )
                    throws DependencyGraphBuilderException
    {
        try
        {
            return objectClazz.getMethod( staticMethod, argClazz ).invoke( null, arg );
        }
        catch ( IllegalAccessException e )
        {
            throw new DependencyGraphBuilderException( e.getMessage(), e );
        }
        catch ( InvocationTargetException e )
        {
            throw new DependencyGraphBuilderException( e.getMessage(), e );
        }
        catch ( NoSuchMethodException e )
        {
            throw new DependencyGraphBuilderException( e.getMessage(), e );
        }
    }
}
