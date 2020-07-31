package org.apache.maven.plugins.dependency.tree.verbose;

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



import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Platform-dependent project properties normalized from ${os.name} and ${os.arch}. Netty uses these to select
 * system-specific dependencies through the
 * <a href='https://github.com/trustin/os-maven-plugin'>os-maven-plugin</a>.
 */
public class OsProperties
{

    public static Map<String, String> detectOsProperties()
    {
        Map<String, String> osMap = new HashMap<String, String>();
        osMap.put( "os.detected.name", osDetectedName() );
        osMap.put( "os.detected.arch", osDetectedArch() );
        osMap.put( "os.detected.classifier", osDetectedName() + "-" + osDetectedArch() );
        return osMap;
    }

    private static String osDetectedName()
    {
        String osNameNormalized = retainFrom( System.getProperty( "os.name" ).toLowerCase( Locale.ENGLISH ) );

        if ( osNameNormalized.startsWith( "macosx" ) || osNameNormalized.startsWith( "osx" ) )
        {
            return "osx";
        }
        else if ( osNameNormalized.startsWith( "windows" ) )
        {
            return "windows";
        }
        // Since we only load the dependency graph, not actually use the
        // dependency, it doesn't matter a great deal which one we pick.
        return "linux";
    }

    private static String osDetectedArch()
    {
        String osArchNormalized = retainFrom( System.getProperty( "os.arch" ).toLowerCase( Locale.ENGLISH ) );
        switch ( osArchNormalized )
        {
            case "x8664":
            case "amd64":
            case "ia32e":
            case "em64t":
            case "x64":
                return "x86_64";
            default:
                return "x86_32";
        }
    }

    private static String retainFrom( String str )
    {
        StringBuilder result = new StringBuilder();
        for ( char c : str.toCharArray() )
        {
            if ( ( c >= 'a' && c <= 'z' ) || ( c >= '0' && c <= '9' ) )
            {
                result.append( c );
            }
        }
        return result.toString();
    }

}
