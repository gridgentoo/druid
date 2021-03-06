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

package org.apache.druid.initialization;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.Binder;
import com.google.inject.ConfigurationException;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
import org.apache.druid.discovery.NodeRole;
import org.apache.druid.guice.ExtensionsConfig;
import org.apache.druid.guice.GuiceInjectors;
import org.apache.druid.guice.JsonConfigProvider;
import org.apache.druid.guice.annotations.LoadScope;
import org.apache.druid.guice.annotations.Self;
import org.apache.druid.java.util.common.ISE;
import org.apache.druid.server.DruidNode;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runners.MethodSorters;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class InitializationTest
{
  @Rule
  public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void test01InitialModulesEmpty()
  {
    Initialization.clearLoadedImplementations();
    Assert.assertEquals(
        "Initial set of loaded modules must be empty",
        0,
        Initialization.getLoadedImplementations(DruidModule.class).size()
    );
  }

  @Test
  public void test02MakeStartupInjector()
  {
    Injector startupInjector = GuiceInjectors.makeStartupInjector();
    Assert.assertNotNull(startupInjector);
    Assert.assertNotNull(startupInjector.getInstance(ObjectMapper.class));
  }

  @Test
  public void test03ClassLoaderExtensionsLoading()
  {
    Injector startupInjector = GuiceInjectors.makeStartupInjector();

    Function<DruidModule, String> fnClassName = new Function<DruidModule, String>()
    {
      @Nullable
      @Override
      public String apply(@Nullable DruidModule input)
      {
        return input.getClass().getName();
      }
    };

    Assert.assertFalse(
        "modules does not contain TestDruidModule",
        Collections2.transform(Initialization.getLoadedImplementations(DruidModule.class), fnClassName)
                    .contains("org.apache.druid.initialization.InitializationTest.TestDruidModule")
    );

    Collection<DruidModule> modules = Initialization.getFromExtensions(
        startupInjector.getInstance(ExtensionsConfig.class),
        DruidModule.class
    );

    Assert.assertTrue(
        "modules contains TestDruidModule",
        Collections2.transform(modules, fnClassName).contains(TestDruidModule.class.getName())
    );
  }

  @Test
  public void test04DuplicateClassLoaderExtensions() throws Exception
  {
    final File extensionDir = temporaryFolder.newFolder();
    Initialization.getLoadersMap()
                  .put(extensionDir, new URLClassLoader(new URL[]{}, Initialization.class.getClassLoader()));

    Collection<DruidModule> modules = Initialization.getFromExtensions(new ExtensionsConfig(), DruidModule.class);

    Set<String> loadedModuleNames = new HashSet<>();
    for (DruidModule module : modules) {
      Assert.assertFalse("Duplicate extensions are loaded", loadedModuleNames.contains(module.getClass().getName()));
      loadedModuleNames.add(module.getClass().getName());
    }

    Initialization.getLoadersMap().clear();
  }

  @Test
  public void test05MakeInjectorWithModules()
  {
    Injector startupInjector = GuiceInjectors.makeStartupInjector();
    Injector injector = Initialization.makeInjectorWithModules(
        startupInjector,
        ImmutableList.<com.google.inject.Module>of(
            new com.google.inject.Module()
            {
              @Override
              public void configure(Binder binder)
              {
                JsonConfigProvider.bindInstance(
                    binder,
                    Key.get(DruidNode.class, Self.class),
                    new DruidNode("test-inject", null, false, null, null, true, false)
                );
              }
            }
        )
    );
    Assert.assertNotNull(injector);
  }

  @Test
  public void test06GetClassLoaderForExtension() throws IOException
  {
    final File some_extension_dir = temporaryFolder.newFolder();
    final File a_jar = new File(some_extension_dir, "a.jar");
    final File b_jar = new File(some_extension_dir, "b.jar");
    final File c_jar = new File(some_extension_dir, "c.jar");
    a_jar.createNewFile();
    b_jar.createNewFile();
    c_jar.createNewFile();
    final URLClassLoader loader = Initialization.getClassLoaderForExtension(some_extension_dir, false);
    final URL[] expectedURLs = new URL[]{a_jar.toURI().toURL(), b_jar.toURI().toURL(), c_jar.toURI().toURL()};
    final URL[] actualURLs = loader.getURLs();
    Arrays.sort(actualURLs, Comparator.comparing(URL::getPath));
    Assert.assertArrayEquals(expectedURLs, actualURLs);
  }

  @Test
  public void testGetLoadedModules()
  {

    Collection<DruidModule> modules = Initialization.getLoadedImplementations(DruidModule.class);
    HashSet<DruidModule> moduleSet = new HashSet<>(modules);

    Collection<DruidModule> loadedModules = Initialization.getLoadedImplementations(DruidModule.class);
    Assert.assertEquals("Set from loaded modules #1 should be same!", modules.size(), loadedModules.size());
    Assert.assertEquals("Set from loaded modules #1 should be same!", moduleSet, new HashSet<>(loadedModules));

    Collection<DruidModule> loadedModules2 = Initialization.getLoadedImplementations(DruidModule.class);
    Assert.assertEquals("Set from loaded modules #2 should be same!", modules.size(), loadedModules2.size());
    Assert.assertEquals("Set from loaded modules #2 should be same!", moduleSet, new HashSet<>(loadedModules2));
  }

  @Test
  public void testGetExtensionFilesToLoad_non_exist_extensions_dir() throws IOException
  {
    final File tmpDir = temporaryFolder.newFolder();
    Assert.assertTrue("could not create missing folder", !tmpDir.exists() || tmpDir.delete());
    Assert.assertArrayEquals(
        "Non-exist root extensionsDir should return an empty array of File",
        new File[]{},
        Initialization.getExtensionFilesToLoad(new ExtensionsConfig()
        {
          @Override
          public String getDirectory()
          {
            return tmpDir.getAbsolutePath();
          }
        })
    );
  }

  @Test(expected = ISE.class)
  public void testGetExtensionFilesToLoad_wrong_type_extensions_dir() throws IOException
  {
    final File extensionsDir = temporaryFolder.newFile();
    final ExtensionsConfig config = new ExtensionsConfig()
    {
      @Override
      public String getDirectory()
      {
        return extensionsDir.getAbsolutePath();
      }
    };
    Initialization.getExtensionFilesToLoad(config);
  }

  @Test
  public void testGetExtensionFilesToLoad_empty_extensions_dir() throws IOException
  {
    final File extensionsDir = temporaryFolder.newFolder();
    final ExtensionsConfig config = new ExtensionsConfig()
    {
      @Override
      public String getDirectory()
      {
        return extensionsDir.getAbsolutePath();
      }
    };

    Assert.assertArrayEquals(
        "Empty root extensionsDir should return an empty array of File",
        new File[]{},
        Initialization.getExtensionFilesToLoad(config)
    );
  }

  /**
   * If druid.extension.load is not specified, Initialization.getExtensionFilesToLoad is supposed to return all the
   * extension folders under root extensions directory.
   */
  @Test
  public void testGetExtensionFilesToLoad_null_load_list() throws IOException
  {
    final File extensionsDir = temporaryFolder.newFolder();
    final ExtensionsConfig config = new ExtensionsConfig()
    {
      @Override
      public String getDirectory()
      {
        return extensionsDir.getAbsolutePath();
      }
    };
    final File mysql_metadata_storage = new File(extensionsDir, "mysql-metadata-storage");
    mysql_metadata_storage.mkdir();

    final File[] expectedFileList = new File[]{mysql_metadata_storage};
    final File[] actualFileList = Initialization.getExtensionFilesToLoad(config);
    Arrays.sort(actualFileList);
    Assert.assertArrayEquals(expectedFileList, actualFileList);
  }

  /**
   * druid.extension.load is specified, Initialization.getExtensionFilesToLoad is supposed to return all the extension
   * folders appeared in the load list.
   */
  @Test
  public void testGetExtensionFilesToLoad_with_load_list() throws IOException
  {
    final File extensionsDir = temporaryFolder.newFolder();

    final File absolutePathExtension = temporaryFolder.newFolder();

    final ExtensionsConfig config = new ExtensionsConfig()
    {
      @Override
      public LinkedHashSet<String> getLoadList()
      {
        return Sets.newLinkedHashSet(Arrays.asList("mysql-metadata-storage", absolutePathExtension.getAbsolutePath()));
      }

      @Override
      public String getDirectory()
      {
        return extensionsDir.getAbsolutePath();
      }
    };
    final File mysql_metadata_storage = new File(extensionsDir, "mysql-metadata-storage");
    final File random_extension = new File(extensionsDir, "random-extensions");

    mysql_metadata_storage.mkdir();
    random_extension.mkdir();

    final File[] expectedFileList = new File[]{mysql_metadata_storage, absolutePathExtension};
    final File[] actualFileList = Initialization.getExtensionFilesToLoad(config);
    Assert.assertArrayEquals(expectedFileList, actualFileList);
  }

  /**
   * druid.extension.load is specified, but contains an extension that is not prepared under root extension directory.
   * Initialization.getExtensionFilesToLoad is supposed to throw ISE.
   */
  @Test(expected = ISE.class)
  public void testGetExtensionFilesToLoad_with_non_exist_item_in_load_list() throws IOException
  {
    final File extensionsDir = temporaryFolder.newFolder();
    final ExtensionsConfig config = new ExtensionsConfig()
    {
      @Override
      public LinkedHashSet<String> getLoadList()
      {
        return Sets.newLinkedHashSet(ImmutableList.of("mysql-metadata-storage"));
      }

      @Override
      public String getDirectory()
      {
        return extensionsDir.getAbsolutePath();
      }
    };
    final File random_extension = new File(extensionsDir, "random-extensions");
    random_extension.mkdir();
    Initialization.getExtensionFilesToLoad(config);
  }

  @Test(expected = ISE.class)
  public void testGetHadoopDependencyFilesToLoad_wrong_type_root_hadoop_depenencies_dir() throws IOException
  {
    final File rootHadoopDependenciesDir = temporaryFolder.newFile();
    final ExtensionsConfig config = new ExtensionsConfig()
    {
      @Override
      public String getHadoopDependenciesDir()
      {
        return rootHadoopDependenciesDir.getAbsolutePath();
      }
    };
    Initialization.getHadoopDependencyFilesToLoad(ImmutableList.of(), config);
  }

  @Test(expected = ISE.class)
  public void testGetHadoopDependencyFilesToLoad_non_exist_version_dir() throws IOException
  {
    final File rootHadoopDependenciesDir = temporaryFolder.newFolder();
    final ExtensionsConfig config = new ExtensionsConfig()
    {
      @Override
      public String getHadoopDependenciesDir()
      {
        return rootHadoopDependenciesDir.getAbsolutePath();
      }
    };
    final File hadoopClient = new File(rootHadoopDependenciesDir, "hadoop-client");
    hadoopClient.mkdir();
    Initialization.getHadoopDependencyFilesToLoad(ImmutableList.of("org.apache.hadoop:hadoop-client:2.3.0"), config);
  }

  @Test
  public void testGetHadoopDependencyFilesToLoad_with_hadoop_coordinates() throws IOException
  {
    final File rootHadoopDependenciesDir = temporaryFolder.newFolder();
    final ExtensionsConfig config = new ExtensionsConfig()
    {
      @Override
      public String getHadoopDependenciesDir()
      {
        return rootHadoopDependenciesDir.getAbsolutePath();
      }
    };
    final File hadoopClient = new File(rootHadoopDependenciesDir, "hadoop-client");
    final File versionDir = new File(hadoopClient, "2.3.0");
    hadoopClient.mkdir();
    versionDir.mkdir();
    final File[] expectedFileList = new File[]{versionDir};
    final File[] actualFileList = Initialization.getHadoopDependencyFilesToLoad(
        ImmutableList.of(
            "org.apache.hadoop:hadoop-client:2.3.0"
        ), config
    );
    Assert.assertArrayEquals(expectedFileList, actualFileList);
  }

  @Test
  public void testGetURLsForClasspath() throws Exception
  {
    File tmpDir1 = temporaryFolder.newFolder();
    File tmpDir2 = temporaryFolder.newFolder();
    File tmpDir3 = temporaryFolder.newFolder();

    File tmpDir1a = new File(tmpDir1, "a.jar");
    tmpDir1a.createNewFile();
    File tmpDir1b = new File(tmpDir1, "b.jar");
    tmpDir1b.createNewFile();
    new File(tmpDir1, "note1.txt").createNewFile();

    File tmpDir2c = new File(tmpDir2, "c.jar");
    tmpDir2c.createNewFile();
    File tmpDir2d = new File(tmpDir2, "d.jar");
    tmpDir2d.createNewFile();
    File tmpDir2e = new File(tmpDir2, "e.JAR");
    tmpDir2e.createNewFile();
    new File(tmpDir2, "note2.txt").createNewFile();

    String cp = tmpDir1.getAbsolutePath() + File.separator + "*"
                + File.pathSeparator
                + tmpDir3.getAbsolutePath()
                + File.pathSeparator
                + tmpDir2.getAbsolutePath() + File.separator + "*";

    // getURLsForClasspath uses listFiles which does NOT guarantee any ordering for the name strings.
    List<URL> urLsForClasspath = Initialization.getURLsForClasspath(cp);
    Assert.assertEquals(Sets.newHashSet(tmpDir1a.toURI().toURL(), tmpDir1b.toURI().toURL()),
                        Sets.newHashSet(urLsForClasspath.subList(0, 2)));
    Assert.assertEquals(tmpDir3.toURI().toURL(), urLsForClasspath.get(2));
    Assert.assertEquals(Sets.newHashSet(tmpDir2c.toURI().toURL(), tmpDir2d.toURI().toURL(), tmpDir2e.toURI().toURL()),
                        Sets.newHashSet(urLsForClasspath.subList(3, 6)));


  }

  @Test
  public void testExtensionsWithSameDirName() throws Exception
  {
    final String extensionName = "some_extension";
    final File tmpDir1 = temporaryFolder.newFolder();
    final File tmpDir2 = temporaryFolder.newFolder();
    final File extension1 = new File(tmpDir1, extensionName);
    final File extension2 = new File(tmpDir2, extensionName);
    Assert.assertTrue(extension1.mkdir());
    Assert.assertTrue(extension2.mkdir());
    final File jar1 = new File(extension1, "jar1.jar");
    final File jar2 = new File(extension2, "jar2.jar");

    Assert.assertTrue(jar1.createNewFile());
    Assert.assertTrue(jar2.createNewFile());

    final ClassLoader classLoader1 = Initialization.getClassLoaderForExtension(extension1, false);
    final ClassLoader classLoader2 = Initialization.getClassLoaderForExtension(extension2, false);

    Assert.assertArrayEquals(new URL[]{jar1.toURI().toURL()}, ((URLClassLoader) classLoader1).getURLs());
    Assert.assertArrayEquals(new URL[]{jar2.toURI().toURL()}, ((URLClassLoader) classLoader2).getURLs());
  }

  public static class TestDruidModule implements DruidModule
  {
    @Override
    public List<? extends Module> getJacksonModules()
    {
      return ImmutableList.of();
    }

    @Override
    public void configure(Binder binder)
    {
      // Do nothing
    }
  }

  @Test
  public void testCreateInjectorWithNodeRoles()
  {
    final DruidNode expected = new DruidNode("test-inject", null, false, null, null, true, false);
    Injector startupInjector = GuiceInjectors.makeStartupInjector();
    Injector injector = Initialization.makeInjectorWithModules(
        ImmutableSet.of(new NodeRole("role1"), new NodeRole("role2")),
        startupInjector,
        ImmutableList.of(
            binder -> JsonConfigProvider.bindInstance(
                binder,
                Key.get(DruidNode.class, Self.class),
                expected
            )
        )
    );
    Assert.assertNotNull(injector);
    Assert.assertEquals(expected, injector.getInstance(Key.get(DruidNode.class, Self.class)));
  }

  @Test
  public void testCreateInjectorWithNodeRoleFilter_moduleNotLoaded()
  {
    final DruidNode expected = new DruidNode("test-inject", null, false, null, null, true, false);
    Injector startupInjector = GuiceInjectors.makeStartupInjector();
    Injector injector = Initialization.makeInjectorWithModules(
        ImmutableSet.of(new NodeRole("role1"), new NodeRole("role2")),
        startupInjector,
        ImmutableList.of(
            (com.google.inject.Module) binder -> JsonConfigProvider.bindInstance(
                binder,
                Key.get(DruidNode.class, Self.class),
                expected
            ),
            new LoadOnAnnotationTestModule()
        )
    );
    Assert.assertNotNull(injector);
    Assert.assertEquals(expected, injector.getInstance(Key.get(DruidNode.class, Self.class)));
    Assert.assertThrows(
        "Guice configuration errors",
        ConfigurationException.class,
        () -> injector.getInstance(Key.get(String.class, Names.named("emperor")))
    );
  }

  @Test
  public void testCreateInjectorWithNodeRoleFilterUsingAnnotation_moduleLoaded()
  {
    final DruidNode expected = new DruidNode("test-inject", null, false, null, null, true, false);
    Injector startupInjector = GuiceInjectors.makeStartupInjector();
    Injector injector = Initialization.makeInjectorWithModules(
        ImmutableSet.of(new NodeRole("role1"), new NodeRole("druid")),
        startupInjector,
        ImmutableList.of(
            (com.google.inject.Module) binder -> JsonConfigProvider.bindInstance(
                binder,
                Key.get(DruidNode.class, Self.class),
                expected
            ),
            new LoadOnAnnotationTestModule()
        )
    );
    Assert.assertNotNull(injector);
    Assert.assertEquals(expected, injector.getInstance(Key.get(DruidNode.class, Self.class)));
    Assert.assertEquals("I am Druid", injector.getInstance(Key.get(String.class, Names.named("emperor"))));
  }

  @LoadScope(roles = {"emperor", "druid"})
  private static class LoadOnAnnotationTestModule implements com.google.inject.Module
  {
    @Override
    public void configure(Binder binder)
    {
      binder.bind(String.class).annotatedWith(Names.named("emperor")).toInstance("I am Druid");
    }
  }

  @Test
  public void testCreateInjectorWithNodeRoleFilterUsingInject_moduleNotLoaded()
  {
    final Set<NodeRole> nodeRoles = ImmutableSet.of(new NodeRole("role1"), new NodeRole("role2"));
    final DruidNode expected = new DruidNode("test-inject", null, false, null, null, true, false);
    Injector startupInjector = GuiceInjectors.makeStartupInjectorWithModules(
        ImmutableList.of(
            binder -> {
              Multibinder<NodeRole> selfBinder = Multibinder.newSetBinder(binder, NodeRole.class, Self.class);
              nodeRoles.forEach(nodeRole -> selfBinder.addBinding().toInstance(nodeRole));
            }
        )
    );
    Injector injector = Initialization.makeInjectorWithModules(
        nodeRoles,
        startupInjector,
        ImmutableList.of(
            (com.google.inject.Module) binder -> JsonConfigProvider.bindInstance(
                binder,
                Key.get(DruidNode.class, Self.class),
                expected
            ),
            new NodeRolesInjectTestModule()
        )
    );
    Assert.assertNotNull(injector);
    Assert.assertEquals(expected, injector.getInstance(Key.get(DruidNode.class, Self.class)));
    Assert.assertThrows(
        "Guice configuration errors",
        ConfigurationException.class,
        () -> injector.getInstance(Key.get(String.class, Names.named("emperor")))
    );
  }

  @Test
  public void testCreateInjectorWithNodeRoleFilterUsingInject_moduleLoaded()
  {
    final Set<NodeRole> nodeRoles = ImmutableSet.of(new NodeRole("role1"), new NodeRole("druid"));
    final DruidNode expected = new DruidNode("test-inject", null, false, null, null, true, false);
    Injector startupInjector = GuiceInjectors.makeStartupInjectorWithModules(
        ImmutableList.of(
            binder -> {
              Multibinder<NodeRole> selfBinder = Multibinder.newSetBinder(binder, NodeRole.class, Self.class);
              nodeRoles.forEach(nodeRole -> selfBinder.addBinding().toInstance(nodeRole));
            }
        )
    );
    Injector injector = Initialization.makeInjectorWithModules(
        nodeRoles,
        startupInjector,
        ImmutableList.of(
            (com.google.inject.Module) binder -> JsonConfigProvider.bindInstance(
                binder,
                Key.get(DruidNode.class, Self.class),
                expected
            ),
            new NodeRolesInjectTestModule()
        )
    );
    Assert.assertNotNull(injector);
    Assert.assertEquals(expected, injector.getInstance(Key.get(DruidNode.class, Self.class)));
    Assert.assertEquals("I am Druid", injector.getInstance(Key.get(String.class, Names.named("emperor"))));
  }

  private static class NodeRolesInjectTestModule implements com.google.inject.Module
  {
    private Set<NodeRole> nodeRoles;

    @Inject
    public void init(@Self Set<NodeRole> nodeRoles)
    {
      this.nodeRoles = nodeRoles;
    }

    @Override
    public void configure(Binder binder)
    {
      if (nodeRoles.contains(new NodeRole("emperor")) || nodeRoles.contains(new NodeRole("druid"))) {
        binder.bind(String.class).annotatedWith(Names.named("emperor")).toInstance("I am Druid");
      }
    }
  }
}
