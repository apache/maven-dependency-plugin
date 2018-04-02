package org.apache.maven.plugins.dependency.fromConfiguration;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/**
 * @author Olivier Lamy
 * @since 2.7
 */
public class ProcessArtifactItemsRequest
{
    /**
     * remove the version from the filename.
     */
    private boolean removeVersion;

    /**
     * remove the classifier from the filename.
     */
    private boolean removeClassifier;

    /**
     * prepend the groupId to the filename.
     */
    private boolean prependGroupId;

    /**
     * use the baseVersion of the artifact instead of version for the filename.
     */
    private boolean useBaseVersion;

    /**
     * Default ctor.
     */
    public ProcessArtifactItemsRequest()
    {
        // no op
    }

    /**
     * @param removeVersion {@link #removeVersion}
     * @param prependGroupId {@link #prependGroupId}
     * @param useBaseVersion {@link #useBaseVersion}
     * @param removeClassifier {@link #removeClassifier}
     */
    public ProcessArtifactItemsRequest( boolean removeVersion, boolean prependGroupId, boolean useBaseVersion,
                                        boolean removeClassifier )
    {
        this.removeVersion = removeVersion;
        this.prependGroupId = prependGroupId;
        this.useBaseVersion = useBaseVersion;
        this.removeClassifier = removeClassifier;
    }

    /**
     * @return {@link #removeVersion}
     */
    public boolean isRemoveVersion()
    {
        return removeVersion;
    }

    /**
     * @param removeVersion {@link #removeVersion}
     */
    public void setRemoveVersion( boolean removeVersion )
    {
        this.removeVersion = removeVersion;
    }

    /**
     * @return {@link #removeClassifier}
     */
    public boolean isRemoveClassifier()
    {
        return removeClassifier;
    }

    /**
     * @param removeClassifier {@link #removeClassifier}
     */
    public void setRemoveClassifier( boolean removeClassifier )
    {
        this.removeClassifier = removeClassifier;
    }

    /**
     * @param theRemoveVersion {@link #removeVersion}
     * @return {@link ProcessArtifactItemsRequest}
     */
    public ProcessArtifactItemsRequest removeVersion( boolean theRemoveVersion )
    {
        this.removeVersion = theRemoveVersion;
        return this;
    }

    /**
     * @return {@link #prependGroupId}
     */
    public boolean isPrependGroupId()
    {
        return prependGroupId;
    }

    /**
     * @param prependGroupId {@link #prependGroupId}
     */
    public void setPrependGroupId( boolean prependGroupId )
    {
        this.prependGroupId = prependGroupId;
    }

    /**
     * @param thePrependGroupId {@link #prependGroupId}
     * @return {@link ProcessArtifactItemsRequest}
     */
    public ProcessArtifactItemsRequest prependGroupId( boolean thePrependGroupId )
    {
        this.prependGroupId = thePrependGroupId;
        return this;
    }

    /**
     * @return {@link #useBaseVersion}
     */
    public boolean isUseBaseVersion()
    {
        return useBaseVersion;
    }

    /**
     * @param useBaseVersion {@link #useBaseVersion}
     */
    public void setUseBaseVersion( boolean useBaseVersion )
    {
        this.useBaseVersion = useBaseVersion;
    }

    /**
     * @param theUseBaseVersion {@link #useBaseVersion}
     * @return {@link ProcessArtifactItemsRequest}
     */
    public ProcessArtifactItemsRequest useBaseVersion( boolean theUseBaseVersion )
    {
        this.useBaseVersion = theUseBaseVersion;
        return this;
    }
}
