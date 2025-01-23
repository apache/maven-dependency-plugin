/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.plugins.dependency.utils;

import org.apache.maven.plugin.logging.Log;

/**
 * This logger implements both types of logs currently in use and turns off logs.
 *
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 */
public class DependencySilentLog implements Log {
    /**
     * @return <code>false</code>
     * @see org.apache.maven.plugin.logging.Log#isDebugEnabled()
     */
    @Override
    public boolean isDebugEnabled() {
        return false;
    }

    /**
     * By default, do nothing.
     *
     * @see org.apache.maven.plugin.logging.Log#debug(java.lang.CharSequence)
     */
    @Override
    public void debug(CharSequence content) {
        // nop
    }

    /**
     * By default, do nothing.
     *
     * @see org.apache.maven.plugin.logging.Log#debug(java.lang.CharSequence, java.lang.Throwable)
     */
    @Override
    public void debug(CharSequence content, Throwable error) {
        // nop
    }

    /**
     * By default, do nothing.
     *
     * @see org.apache.maven.plugin.logging.Log#debug(java.lang.Throwable)
     */
    @Override
    public void debug(Throwable error) {
        // nop
    }

    /**
     * @return <code>false</code>
     * @see org.apache.maven.plugin.logging.Log#isInfoEnabled()
     */
    @Override
    public boolean isInfoEnabled() {
        return false;
    }

    /**
     * By default, do nothing.
     *
     * @see org.apache.maven.plugin.logging.Log#info(java.lang.CharSequence)
     */
    @Override
    public void info(CharSequence content) {
        // nop
    }

    /**
     * By default, do nothing.
     *
     * @see org.apache.maven.plugin.logging.Log#info(java.lang.CharSequence, java.lang.Throwable)
     */
    @Override
    public void info(CharSequence content, Throwable error) {
        // nop
    }

    /**
     * By default, do nothing.
     *
     * @see org.apache.maven.plugin.logging.Log#info(java.lang.Throwable)
     */
    @Override
    public void info(Throwable error) {
        // nop
    }

    /**
     * By default, do nothing.
     *
     * @see org.apache.maven.plugin.logging.Log#isWarnEnabled()
     */
    @Override
    public boolean isWarnEnabled() {
        // nop
        return false;
    }

    /**
     * By default, do nothing.
     *
     * @see org.apache.maven.plugin.logging.Log#warn(java.lang.CharSequence)
     */
    @Override
    public void warn(CharSequence content) {
        // nop
    }

    /**
     * By default, do nothing.
     *
     * @see org.apache.maven.plugin.logging.Log#warn(java.lang.CharSequence, java.lang.Throwable)
     */
    @Override
    public void warn(CharSequence content, Throwable error) {
        // nop
    }

    /**
     * By default, do nothing.
     *
     * @see org.apache.maven.plugin.logging.Log#warn(java.lang.Throwable)
     */
    @Override
    public void warn(Throwable error) {
        // nop
    }

    /**
     * @return <code>false</code>
     * @see org.apache.maven.plugin.logging.Log#isErrorEnabled()
     */
    @Override
    public boolean isErrorEnabled() {
        return false;
    }

    /**
     * By default, do nothing.
     *
     * @see org.apache.maven.plugin.logging.Log#error(java.lang.CharSequence)
     */
    @Override
    public void error(CharSequence content) {
        // nop
    }

    /**
     * By default, do nothing.
     *
     * @see org.apache.maven.plugin.logging.Log#error(java.lang.CharSequence, java.lang.Throwable)
     */
    @Override
    public void error(CharSequence content, Throwable error) {
        // nop
    }

    /**
     * By default, do nothing.
     *
     * @see org.apache.maven.plugin.logging.Log#error(java.lang.Throwable)
     */
    @Override
    public void error(Throwable error) {
        // nop
    }
}
