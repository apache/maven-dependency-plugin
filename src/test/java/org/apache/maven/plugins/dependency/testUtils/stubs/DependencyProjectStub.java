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
package org.apache.maven.plugins.dependency.testUtils.stubs;

import java.io.File;
import java.io.Writer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Build;
import org.apache.maven.model.CiManagement;
import org.apache.maven.model.Contributor;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Developer;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.model.Extension;
import org.apache.maven.model.IssueManagement;
import org.apache.maven.model.License;
import org.apache.maven.model.MailingList;
import org.apache.maven.model.Model;
import org.apache.maven.model.Organization;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.Prerequisites;
import org.apache.maven.model.Profile;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.model.Reporting;
import org.apache.maven.model.Repository;
import org.apache.maven.model.Resource;
import org.apache.maven.model.Scm;
import org.apache.maven.plugin.testing.stubs.DefaultArtifactHandlerStub;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.PlexusTestCase;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * very simple stub of maven project, going to take a lot of work to make it useful as a stub though
 */
public class DependencyProjectStub extends MavenProject {
    private String groupId;

    private String artifactId;

    private String name;

    private Model model;

    private MavenProject parent;

    private List<Dependency> dependencies;

    private File file;

    private List<MavenProject> collectedProjects;

    private List<Artifact> attachedArtifacts;

    private List<String> compileSourceRoots;

    private List<String> testCompileSourceRoots;

    private List<String> scriptSourceRoots;

    private List<ArtifactRepository> pluginArtifactRepositories;

    private List<Profile> activeProfiles;

    private Set<Artifact> dependencyArtifacts;

    private DependencyManagement dependencyManagement;

    private Artifact artifact;

    private Model originalModel;

    private boolean executionRoot;

    private List<Artifact> compileArtifacts;

    private List<Dependency> compileDependencies;

    private List<Dependency> systemDependencies;

    private List<String> testClasspathElements;

    private List<Dependency> testDependencies;

    private List<String> systemClasspathElements;

    private List<Artifact> systemArtifacts;

    private List<Artifact> testArtifacts;

    private List<Artifact> runtimeArtifacts;

    private List<Dependency> runtimeDependencies;

    private List<String> runtimeClasspathElements;

    private String modelVersion;

    private String packaging;

    private String inceptionYear;

    private String url;

    private String description;

    private String version;

    private String defaultGoal;

    private Set<Artifact> artifacts;

    private Properties properties;

    public DependencyProjectStub() {
        super((Model) null);
    }

    // kinda dangerous...
    public DependencyProjectStub(Model model) {
        // super(model);
        super((Model) null);
    }

    // kinda dangerous...
    public DependencyProjectStub(MavenProject project) {
        // super(project);
        super((Model) null);
    }

    @Override
    @Deprecated
    public String getModulePathAdjustment(MavenProject mavenProject) {
        return "";
    }

    @Override
    public Artifact getArtifact() {
        if (artifact == null) {
            ArtifactHandler ah = new DefaultArtifactHandlerStub("jar", null);

            VersionRange vr = VersionRange.createFromVersion("1.0");
            Artifact art = new DefaultArtifact("group", "artifact", vr, Artifact.SCOPE_COMPILE, "jar", null, ah, false);
            setArtifact(art);
        }
        return artifact;
    }

    @Override
    public void setArtifact(Artifact artifact) {
        this.artifact = artifact;
    }

    @Override
    public Model getModel() {
        return model;
    }

    @Override
    public MavenProject getParent() {
        return parent;
    }

    @Override
    public void setParent(MavenProject mavenProject) {
        this.parent = mavenProject;
    }

    @Override
    public void setRemoteArtifactRepositories(List<ArtifactRepository> list) {}

    @Override
    public List<ArtifactRepository> getRemoteArtifactRepositories() {
        return Collections.emptyList();
    }

    @Override
    public List<RemoteRepository> getRemoteProjectRepositories() {
        return Collections.emptyList();
    }

    @Override
    public boolean hasParent() {
        return (parent != null);
    }

    @Override
    public File getFile() {
        return file;
    }

    @Override
    public void setFile(File file) {
        this.file = file;
    }

    @Override
    public File getBasedir() {
        return new File(PlexusTestCase.getBasedir());
    }

    @Override
    public void setDependencies(List<Dependency> list) {
        dependencies = list;
    }

    @Override
    public List<Dependency> getDependencies() {
        if (dependencies == null) {
            dependencies = Collections.emptyList();
        }
        return dependencies;
    }

    public void setDependencyManagement(DependencyManagement depMgt) {
        this.dependencyManagement = depMgt;
    }

    @Override
    public DependencyManagement getDependencyManagement() {
        if (dependencyManagement == null) {
            dependencyManagement = new DependencyManagement();
        }

        return dependencyManagement;
    }

    @Override
    public void addCompileSourceRoot(String string) {
        if (compileSourceRoots == null) {
            compileSourceRoots = Collections.singletonList(string);
        } else {
            compileSourceRoots.add(string);
        }
    }

    @Override
    @Deprecated
    public void addScriptSourceRoot(String string) {
        if (scriptSourceRoots == null) {
            scriptSourceRoots = Collections.singletonList(string);
        } else {
            scriptSourceRoots.add(string);
        }
    }

    @Override
    public void addTestCompileSourceRoot(String string) {
        if (testCompileSourceRoots == null) {
            testCompileSourceRoots = Collections.singletonList(string);
        } else {
            testCompileSourceRoots.add(string);
        }
    }

    @Override
    public List<String> getCompileSourceRoots() {
        return compileSourceRoots;
    }

    @Override
    @Deprecated
    public List<String> getScriptSourceRoots() {
        return scriptSourceRoots;
    }

    @Override
    public List<String> getTestCompileSourceRoots() {
        return testCompileSourceRoots;
    }

    @Override
    public List<String> getCompileClasspathElements() {
        return compileSourceRoots;
    }

    @Override
    @Deprecated
    public List<Artifact> getCompileArtifacts() {
        return compileArtifacts;
    }

    @Override
    @Deprecated
    public List<Dependency> getCompileDependencies() {
        return compileDependencies;
    }

    @Override
    public List<String> getTestClasspathElements() {
        return testClasspathElements;
    }

    @Override
    @Deprecated
    public List<Artifact> getTestArtifacts() {
        return testArtifacts;
    }

    @Override
    @Deprecated
    public List<Dependency> getTestDependencies() {
        return testDependencies;
    }

    @Override
    public List<String> getRuntimeClasspathElements() {
        return runtimeClasspathElements;
    }

    @Override
    @Deprecated
    public List<Artifact> getRuntimeArtifacts() {
        return runtimeArtifacts;
    }

    @Override
    @Deprecated
    public List<Dependency> getRuntimeDependencies() {
        return runtimeDependencies;
    }

    @Override
    @Deprecated
    public List<String> getSystemClasspathElements() {
        return systemClasspathElements;
    }

    @Override
    @Deprecated
    public List<Artifact> getSystemArtifacts() {
        return systemArtifacts;
    }

    @Override
    public void setAttachedArtifacts(List<Artifact> attachedArtifacts) {
        this.attachedArtifacts = attachedArtifacts;
    }

    @Override
    public void setCompileSourceRoots(List<String> compileSourceRoots) {
        this.compileSourceRoots = compileSourceRoots;
    }

    @Override
    public void setTestCompileSourceRoots(List<String> testCompileSourceRoots) {
        this.testCompileSourceRoots = testCompileSourceRoots;
    }

    @Override
    @Deprecated
    public void setScriptSourceRoots(List<String> scriptSourceRoots) {
        this.scriptSourceRoots = scriptSourceRoots;
    }

    @Override
    public void setModel(Model model) {
        this.model = model;
    }

    @Override
    @Deprecated
    public List<Dependency> getSystemDependencies() {
        return systemDependencies;
    }

    @Override
    public void setModelVersion(String string) {
        this.modelVersion = string;
    }

    @Override
    public String getModelVersion() {
        return modelVersion;
    }

    @Override
    public String getId() {
        return "";
    }

    @Override
    public void setGroupId(String string) {
        this.groupId = string;
    }

    @Override
    public String getGroupId() {
        return groupId;
    }

    @Override
    public void setArtifactId(String string) {
        this.artifactId = string;
    }

    @Override
    public String getArtifactId() {
        return artifactId;
    }

    @Override
    public void setName(String string) {
        this.name = string;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setVersion(String string) {
        this.version = string;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public String getPackaging() {
        return packaging;
    }

    @Override
    public void setPackaging(String string) {
        this.packaging = string;
    }

    @Override
    public void setInceptionYear(String string) {
        this.inceptionYear = string;
    }

    @Override
    public String getInceptionYear() {
        return inceptionYear;
    }

    @Override
    public void setUrl(String string) {
        this.url = string;
    }

    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public Prerequisites getPrerequisites() {
        return null;
    }

    @Override
    public void setIssueManagement(IssueManagement issueManagement) {}

    @Override
    public CiManagement getCiManagement() {
        return null;
    }

    @Override
    public void setCiManagement(CiManagement ciManagement) {}

    @Override
    public IssueManagement getIssueManagement() {
        return null;
    }

    @Override
    public void setDistributionManagement(DistributionManagement distributionManagement) {}

    @Override
    public DistributionManagement getDistributionManagement() {
        return null;
    }

    @Override
    public void setDescription(String string) {
        this.description = string;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void setOrganization(Organization organization) {}

    @Override
    public Organization getOrganization() {
        return null;
    }

    @Override
    public void setScm(Scm scm) {}

    @Override
    public Scm getScm() {
        return null;
    }

    @Override
    public void setMailingLists(List<MailingList> list) {}

    @Override
    public List<MailingList> getMailingLists() {
        return Collections.emptyList();
    }

    @Override
    public void addMailingList(MailingList mailingList) {}

    @Override
    public void setDevelopers(List<Developer> list) {}

    @Override
    public List<Developer> getDevelopers() {
        return Collections.emptyList();
    }

    @Override
    public void addDeveloper(Developer developer) {}

    @Override
    public void setContributors(List<Contributor> list) {}

    @Override
    public List<Contributor> getContributors() {
        return Collections.emptyList();
    }

    @Override
    public void addContributor(Contributor contributor) {}

    @Override
    public void setBuild(Build build) {}

    @Override
    public Build getBuild() {
        return null;
    }

    @Override
    public List<Resource> getResources() {
        return Collections.emptyList();
    }

    @Override
    public List<Resource> getTestResources() {
        return Collections.emptyList();
    }

    @Override
    public void addResource(Resource resource) {}

    @Override
    public void addTestResource(Resource resource) {}

    @Override
    @Deprecated
    public void setReporting(Reporting reporting) {}

    @Override
    @Deprecated
    public Reporting getReporting() {
        return null;
    }

    @Override
    public void setLicenses(List<License> list) {}

    @Override
    public List<License> getLicenses() {
        return Collections.emptyList();
    }

    @Override
    public void addLicense(License license) {}

    @Override
    public void setArtifacts(Set<Artifact> set) {
        this.artifacts = set;
    }

    @Override
    public Set<Artifact> getArtifacts() {
        if (artifacts == null) {
            return Collections.emptySet();
        } else {
            return artifacts;
        }
    }

    @Override
    public Map<String, Artifact> getArtifactMap() {
        return Collections.emptyMap();
    }

    @Override
    public void setPluginArtifacts(Set<Artifact> set) {}

    @Override
    public Set<Artifact> getPluginArtifacts() {
        return Collections.emptySet();
    }

    @Override
    public Map<String, Artifact> getPluginArtifactMap() {
        return Collections.emptyMap();
    }

    @Override
    @Deprecated
    public void setReportArtifacts(Set<Artifact> set) {}

    @Override
    @Deprecated
    public Set<Artifact> getReportArtifacts() {
        return Collections.emptySet();
    }

    @Override
    public Map<String, Artifact> getReportArtifactMap() {
        return Collections.emptyMap();
    }

    @Override
    @Deprecated
    public void setExtensionArtifacts(Set<Artifact> set) {}

    @Override
    @Deprecated
    public Set<Artifact> getExtensionArtifacts() {
        return Collections.emptySet();
    }

    @Override
    @Deprecated
    public Map<String, Artifact> getExtensionArtifactMap() {
        return Collections.emptyMap();
    }

    @Override
    public void setParentArtifact(Artifact artifact) {}

    @Override
    public Artifact getParentArtifact() {
        return null;
    }

    @Override
    public List<Repository> getRepositories() {
        return Collections.emptyList();
    }

    @Override
    @Deprecated
    public List<ReportPlugin> getReportPlugins() {
        return Collections.emptyList();
    }

    @Override
    public List<Plugin> getBuildPlugins() {
        return Collections.emptyList();
    }

    @Override
    public List<String> getModules() {
        return Collections.singletonList("");
    }

    @Override
    public PluginManagement getPluginManagement() {
        return null;
    }

    @Override
    public List<MavenProject> getCollectedProjects() {
        return collectedProjects;
    }

    @Override
    public void setCollectedProjects(List<MavenProject> list) {
        this.collectedProjects = list;
    }

    @Override
    public void setPluginArtifactRepositories(List<ArtifactRepository> list) {
        this.pluginArtifactRepositories = list;
    }

    @Override
    public List<ArtifactRepository> getPluginArtifactRepositories() {
        return pluginArtifactRepositories;
    }

    @Override
    public ArtifactRepository getDistributionManagementArtifactRepository() {
        return null;
    }

    @Override
    public List<Repository> getPluginRepositories() {
        return Collections.emptyList();
    }

    @Override
    public void setActiveProfiles(List<Profile> list) {
        activeProfiles = list;
    }

    @Override
    public List<Profile> getActiveProfiles() {
        return activeProfiles;
    }

    @Override
    public void addAttachedArtifact(Artifact theArtifact) {
        if (attachedArtifacts == null) {
            this.attachedArtifacts = Collections.singletonList(theArtifact);
        } else {
            attachedArtifacts.add(theArtifact);
        }
    }

    @Override
    public List<Artifact> getAttachedArtifacts() {
        return attachedArtifacts;
    }

    @Override
    public Xpp3Dom getGoalConfiguration(String string, String string1, String string2, String string3) {
        return null;
    }

    @Override
    @Deprecated
    public Xpp3Dom getReportConfiguration(String string, String string1, String string2) {
        return null;
    }

    @Override
    public MavenProject getExecutionProject() {
        return null;
    }

    @Override
    public void setExecutionProject(MavenProject mavenProject) {}

    @Override
    @Deprecated
    public void writeModel(Writer writer) {}

    @Override
    @Deprecated
    public void writeOriginalModel(Writer writer) {}

    @Override
    public Set<Artifact> getDependencyArtifacts() {
        return dependencyArtifacts;
    }

    @Override
    public void setDependencyArtifacts(Set<Artifact> set) {
        this.dependencyArtifacts = set;
    }

    @Override
    public void setReleaseArtifactRepository(ArtifactRepository artifactRepository) {
        // this.releaseArtifactRepository = artifactRepository;
    }

    @Override
    public void setSnapshotArtifactRepository(ArtifactRepository artifactRepository) {
        // this.snapshotArtifactRepository = artifactRepository;
    }

    @Override
    public void setOriginalModel(Model model) {
        this.originalModel = model;
    }

    @Override
    public Model getOriginalModel() {
        return originalModel;
    }

    @Override
    public List<Extension> getBuildExtensions() {
        return Collections.emptyList();
    }

    @Override
    @Deprecated
    public Set<Artifact> createArtifacts(
            ArtifactFactory artifactFactory, String string, ArtifactFilter artifactFilter) {
        return Collections.emptySet();
    }

    @Override
    public void addProjectReference(MavenProject mavenProject) {}

    @Override
    @Deprecated
    public void attachArtifact(String string, String string1, File theFile) {}

    @Override
    public Properties getProperties() {
        if (properties == null) {
            properties = new Properties();
        }
        return properties;
    }

    @Override
    public List<String> getFilters() {
        return Collections.singletonList("");
    }

    @Override
    public Map<String, MavenProject> getProjectReferences() {
        return Collections.emptyMap();
    }

    @Override
    public boolean isExecutionRoot() {
        return executionRoot;
    }

    @Override
    public void setExecutionRoot(boolean b) {
        this.executionRoot = b;
    }

    @Override
    public String getDefaultGoal() {
        return defaultGoal;
    }

    @Override
    @Deprecated
    public Artifact replaceWithActiveArtifact(Artifact theArtifact) {
        return null;
    }
}
