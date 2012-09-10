/*
 *  Copyright (C) 2010-2012 Axel Morgner
 *
 *  This file is part of structr <http://structr.org>.
 *
 *  structr is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  structr is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with structr.  If not, see <http://www.gnu.org/licenses/>.
 */



package org.structr.common;

import junit.framework.TestCase;

import org.apache.commons.io.FileUtils;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.RelationshipType;

import org.structr.common.error.FrameworkException;
import org.structr.core.Command;
import org.structr.core.Services;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.AbstractRelationship;
import org.structr.core.entity.GenericNode;
import org.structr.core.node.CreateNodeCommand;
import org.structr.core.node.CreateRelationshipCommand;
import org.structr.core.node.GraphDatabaseCommand;
import org.structr.core.node.StructrTransaction;
import org.structr.core.node.TransactionCommand;

//~--- JDK imports ------------------------------------------------------------

import java.io.File;
import java.io.IOException;

import java.net.URL;

import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

//~--- classes ----------------------------------------------------------------

/**
 * Base class for all structr tests
 *
 * All tests are executed in superuser context
 *
 * @author Axel Morgner
 */
public class StructrTest extends TestCase {

	private static final Logger logger = Logger.getLogger(StructrTest.class.getName());

	//~--- fields ---------------------------------------------------------

	protected Map<String, String> context = new ConcurrentHashMap<String, String>(20, 0.9f, 8);
	protected Command createNodeCommand;
	protected Command createRelationshipCommand;
	protected Command graphDbCommand;
	protected SecurityContext securityContext;
	protected Command transactionCommand;

	//~--- methods --------------------------------------------------------

	protected void init() {

		Date now       = new Date();
		long timestamp = now.getTime();

		context.put(Services.CONFIGURED_SERVICES, "ModuleService NodeService");
		context.put(Services.APPLICATION_TITLE, "structr unit test app" + timestamp);
		context.put(Services.TMP_PATH, "/tmp/");
		context.put(Services.BASE_PATH, "/tmp/structr-test-" + timestamp);
		context.put(Services.DATABASE_PATH, "/tmp/structr-test-" + timestamp + "/db");
		context.put(Services.FILES_PATH, "/tmp/structr-test-" + timestamp + "/files");
		context.put(Services.TCP_PORT, "13465");
		context.put(Services.SERVER_IP, "127.0.0.1");
		context.put(Services.UDP_PORT, "13466");
		context.put(Services.SUPERUSER_USERNAME, "superadmin");
		context.put(Services.SUPERUSER_PASSWORD, "sehrgeheim");
		Services.initialize(context);

	}

	public void test00DbAvailable() {

		try {

			GraphDatabaseService graphDb = (GraphDatabaseService) graphDbCommand.execute();

			assertTrue(graphDb != null);

		} catch (FrameworkException ex) {

			logger.log(Level.SEVERE, ex.toString());
			fail("Unexpected exception");

		}

	}

	@Override
	protected void tearDown() throws Exception {

		Services.shutdown();

		File testDir = new File(context.get(Services.BASE_PATH));

		if (testDir.isDirectory()) {

			FileUtils.deleteDirectory(testDir);
		} else {

			testDir.delete();
		}

		super.tearDown();

	}

	/**
	 * Recursive method used to find all classes in a given directory and subdirs.
	 *
	 * @param directory   The base directory
	 * @param packageName The package name for classes found inside the base directory
	 * @return The classes
	 * @throws ClassNotFoundException
	 */
	private static List<Class> findClasses(File directory, String packageName) throws ClassNotFoundException {

		List<Class> classes = new ArrayList<Class>();

		if (!directory.exists()) {

			return classes;
		}

		File[] files = directory.listFiles();

		for (File file : files) {

			if (file.isDirectory()) {

				assert !file.getName().contains(".");

				classes.addAll(findClasses(file, packageName + "." + file.getName()));

			} else if (file.getName().endsWith(".class")) {

				classes.add(Class.forName(packageName + '.' + file.getName().substring(0, file.getName().length() - 6)));
			}

		}

		return classes;

	}

	protected List<AbstractNode> createTestNodes(final String type, final int number) throws FrameworkException {

		final Map<String, Object> props = new HashMap<String, Object>();

		props.put(AbstractNode.Key.type.name(), type);

		return (List<AbstractNode>) transactionCommand.execute(new StructrTransaction() {

			@Override
			public Object execute() throws FrameworkException {

				List<AbstractNode> nodes = new LinkedList<AbstractNode>();

				for (int i = 0; i < number; i++) {

					nodes.add((AbstractNode) createNodeCommand.execute(props));
				}

				return nodes;

			}

		});

	}

	protected List<AbstractRelationship> createTestRelationships(final RelationshipType relType, final int number) throws FrameworkException {

		List<AbstractNode> nodes     = createTestNodes("UnknownTestType", 2);
		final AbstractNode startNode = nodes.get(0);
		final AbstractNode endNode   = nodes.get(1);

		return (List<AbstractRelationship>) transactionCommand.execute(new StructrTransaction() {

			@Override
			public Object execute() throws FrameworkException {

				List<AbstractRelationship> rels = new LinkedList<AbstractRelationship>();

				for (int i = 0; i < number; i++) {

					rels.add((AbstractRelationship) createRelationshipCommand.execute(startNode, endNode, relType));
				}

				return rels;

			}

		});

	}

	//~--- get methods ----------------------------------------------------

	/**
	 * Get classes in given package and subpackages, accessible from the context class loader
	 *
	 * @param packageName The base package
	 * @return The classes
	 * @throws ClassNotFoundException
	 * @throws IOException
	 */
	protected static List<Class> getClasses(String packageName) throws ClassNotFoundException, IOException {

		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

		assert classLoader != null;

		String path                = packageName.replace('.', '/');
		Enumeration<URL> resources = classLoader.getResources(path);
		List<File> dirs            = new ArrayList<File>();

		while (resources.hasMoreElements()) {

			URL resource = resources.nextElement();

			dirs.add(new File(resource.getFile()));

		}

		List<Class> classList = new ArrayList<Class>();

		for (File directory : dirs) {

			classList.addAll(findClasses(directory, packageName));
		}

		return classList;

	}

	//~--- set methods ----------------------------------------------------

	@Override
	protected void setUp() throws Exception {

		init();

		securityContext           = SecurityContext.getSuperUserInstance();
		createNodeCommand         = Services.command(securityContext, CreateNodeCommand.class);
		createRelationshipCommand = Services.command(securityContext, CreateRelationshipCommand.class);
		transactionCommand        = Services.command(securityContext, TransactionCommand.class);
		graphDbCommand            = Services.command(securityContext, GraphDatabaseCommand.class);

	}

}