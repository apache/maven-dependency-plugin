package org.apache.maven.plugins.dependency.testUtils.stubs;

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

import org.apache.maven.plugin.logging.Log;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public class CapturingLog implements Log
{
    private static final String NEWLINE = System.lineSeparator();

    private StringBuffer buffer = new StringBuffer();

    @Override
    public boolean isDebugEnabled()
    {
        return true;
    }

    @Override
    public void debug( CharSequence content )
    {
        buffer.append("DEBUG ");
        buffer.append(content);
        buffer.append(NEWLINE);
    }

    @Override
    public void debug( CharSequence content, Throwable error )
    {
        buffer.append("DEBUG ");
        buffer.append(content);
        buffer.append(' ');
        buffer.append( printThrowable( error ) );
        buffer.append(NEWLINE);
    }

    @Override
    public void debug( Throwable error )
    {
        buffer.append("DEBUG ");
        buffer.append( printThrowable( error ) );
        buffer.append(NEWLINE);
    }

    @Override
    public boolean isInfoEnabled()
    {
        return true;
    }

    @Override
    public void info( CharSequence content )
    {
        buffer.append("INFO ");
        buffer.append(content);
        buffer.append(NEWLINE);
    }

    @Override
    public void info( CharSequence content, Throwable error )
    {
        buffer.append("INFO ");
        buffer.append(content);
        buffer.append(' ');
        buffer.append( printThrowable( error ) );
        buffer.append(NEWLINE);
    }

    @Override
    public void info( Throwable error )
    {
        buffer.append("INFO ");
        buffer.append( printThrowable( error ) );
        buffer.append(NEWLINE);
    }

    @Override
    public boolean isWarnEnabled() {
        return true;
    }

    @Override
    public void warn( CharSequence content )
    {
        buffer.append("WARNING ");
        buffer.append(content);
        buffer.append(NEWLINE);
    }

    @Override
    public void warn( CharSequence content, Throwable error )
    {
        buffer.append("WARNING ");
        buffer.append(content);
        buffer.append(' ');
        buffer.append( printThrowable( error ) );
        buffer.append(NEWLINE);
    }

    @Override
    public void warn( Throwable error )
    {
        buffer.append("WARNING ");
        buffer.append( printThrowable( error ) );
        buffer.append(NEWLINE);
    }

    @Override
    public boolean isErrorEnabled()
    {
        return true;
    }

    @Override
    public void error( CharSequence content )
    {
        buffer.append("ERROR ");
        buffer.append(content);
        buffer.append(NEWLINE);
    }

    @Override
    public void error( CharSequence content, Throwable error )
    {
        buffer.append("ERROR ");
        buffer.append(content);
        buffer.append(' ');
        buffer.append( printThrowable( error ) );
        buffer.append(NEWLINE);
    }

    @Override
    public void error( Throwable error )
    {
        buffer.append("ERROR ");
        buffer.append( printThrowable( error ) );
        buffer.append(NEWLINE);
    }

    private String printThrowable( Throwable error )
    {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        error.printStackTrace( new PrintStream( output ) );
        return output.toString();
    }

    public String getContent()
    {
        return this.buffer.toString();
    }
}
