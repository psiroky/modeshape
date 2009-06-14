/*
 * JBoss DNA (http://www.jboss.org/dna)
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * See the AUTHORS.txt file in the distribution for a full listing of 
 * individual contributors. 
 *
 * JBoss DNA is free software. Unless otherwise indicated, all code in JBoss DNA
 * is licensed to you under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * JBoss DNA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.dna.jcr;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.stub;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.jcr.NamespaceRegistry;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import org.jboss.dna.graph.ExecutionContext;
import org.jboss.dna.graph.Graph;
import org.jboss.dna.graph.JaasSecurityContext;
import org.jboss.dna.graph.JcrLexicon;
import org.jboss.dna.graph.connector.RepositoryConnection;
import org.jboss.dna.graph.connector.RepositoryConnectionFactory;
import org.jboss.dna.graph.connector.RepositorySourceException;
import org.jboss.dna.graph.connector.inmemory.InMemoryRepositorySource;
import org.jboss.security.config.IDTrustConfiguration;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoAnnotations.Mock;

/**
 * @author jverhaeg
 */
public class JcrWorkspaceTest {

    private String workspaceName;
    private ExecutionContext context;
    private InMemoryRepositorySource source;
    private JcrWorkspace workspace;
    private RepositoryConnectionFactory connectionFactory;
    private Map<String, Object> sessionAttributes;
    @Mock
    private JcrRepository repository;
    private RepositoryNodeTypeManager repoManager;

    @BeforeClass
    public static void beforeClass() {
        // Initialize IDTrust
        String configFile = "security/jaas.conf.xml";
        IDTrustConfiguration idtrustConfig = new IDTrustConfiguration();

        try {
            idtrustConfig.config(configFile);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Before
    public void beforeEach() throws Exception {
        final String repositorySourceName = "repository";
        workspaceName = "workspace1";

        // Set up the source ...
        source = new InMemoryRepositorySource();
        source.setName(repositorySourceName);
        source.setDefaultWorkspaceName(workspaceName);

        // Set up the execution context ...

        context = new ExecutionContext().with(new JaasSecurityContext("dna-jcr", "superuser", "superuser".toCharArray()));

        // Set up the initial content ...
        Graph graph = Graph.create(source, context);
        graph.create("/a").and().create("/a/b").and().create("/a/b/c").and().create("/b");
        graph.set("booleanProperty").on("/a/b").to(true);
        graph.set("jcr:primaryType").on("/a/b").to("nt:unstructured");
        graph.set("stringProperty").on("/a/b/c").to("value");

        // Make sure the path to the namespaces exists ...
        graph.create("/jcr:system").and().create("/jcr:system/dna:namespaces");

        // Stub out the connection factory ...
        connectionFactory = new RepositoryConnectionFactory() {
            /**
             * {@inheritDoc}
             * 
             * @see org.jboss.dna.graph.connector.RepositoryConnectionFactory#createConnection(java.lang.String)
             */
            @SuppressWarnings( "synthetic-access" )
            public RepositoryConnection createConnection( String sourceName ) throws RepositorySourceException {
                return repositorySourceName.equals(sourceName) ? source.getConnection() : null;
            }
        };

        // Stub out the repository, since we only need a few methods ...
        MockitoAnnotations.initMocks(this);

        repoManager = new RepositoryNodeTypeManager(context);
        try {
            this.repoManager.registerNodeTypes(new CndNodeTypeSource(new String[] {"/org/jboss/dna/jcr/jsr_170_builtins.cnd",
                "/org/jboss/dna/jcr/dna_builtins.cnd"}));
        } catch (RepositoryException re) {
            re.printStackTrace();
            throw new IllegalStateException("Could not load node type definition files", re);
        } catch (IOException ioe) {
            ioe.printStackTrace();
            throw new IllegalStateException("Could not access node type definition files", ioe);
        }

        stub(repository.getRepositorySourceName()).toReturn(repositorySourceName);
        stub(repository.getRepositoryTypeManager()).toReturn(repoManager);
        stub(repository.getConnectionFactory()).toReturn(connectionFactory);

        // Now create the workspace ...
        sessionAttributes = new HashMap<String, Object>();
        workspace = new JcrWorkspace(repository, workspaceName, context, sessionAttributes);
    }

    @Test( expected = UnsupportedOperationException.class )
    public void shouldNotAllowClone() throws Exception {
        workspace.clone(null, null, null, false);
    }

    @Test( expected = IllegalArgumentException.class )
    public void shouldNotAllowCopyFromNullPathToNullPath() throws Exception {
        workspace.copy(null, null);
    }

    @Test
    public void shouldCopyFromPathToAnotherPathInSameWorkspace() throws Exception {
        workspace.copy("/a/b", "/b/b-copy");
    }

    @Test( expected = IllegalArgumentException.class )
    public void shouldNotAllowCopyFromOtherWorkspaceWithNullWorkspace() throws Exception {
        workspace.copy(null, null, null);
    }

    @Test
    public void shouldNotAllowGetAccessibleWorkspaceNames() throws Exception {
        String[] names = workspace.getAccessibleWorkspaceNames();
        assertThat(names.length, is(1));
        assertThat(names[0], is(workspaceName));
    }

    @Test( expected = IllegalArgumentException.class )
    public void shouldNotAllowImportContentHandlerWithNullPath() throws Exception {
        workspace.getImportContentHandler(null, 0);
    }

    @Test
    public void shouldGetImportContentHandlerWithValidPath() throws Exception {
        assertThat(workspace.getImportContentHandler("/b", 0), is(notNullValue()));
    }

    @Test
    public void shouldProvideName() throws Exception {
        assertThat(workspace.getName(), is(workspaceName));
    }

    @Test
    public void shouldProvideNamespaceRegistry() throws Exception {
        NamespaceRegistry registry = workspace.getNamespaceRegistry();
        assertThat(registry, is(notNullValue()));
        assertThat(registry.getURI(JcrLexicon.Namespace.PREFIX), is(JcrLexicon.Namespace.URI));
    }

    @Test
    public void shouldGetNodeTypeManager() throws Exception {
        assertThat(workspace.getNodeTypeManager(), is(notNullValue()));
    }

    @Test( expected = UnsupportedRepositoryOperationException.class )
    public void shouldNotAllowGetObservationManager() throws Exception {
        workspace.getObservationManager();
    }

    @Test
    public void shouldProvideQueryManager() throws Exception {
        assertThat(workspace.getQueryManager(), notNullValue());
    }

    public void shouldCreateQuery() throws Exception {
        String statement = "Some query syntax";

        QueryManager queryManager = workspace.getQueryManager();
        Query query = queryManager.createQuery(statement, Query.XPATH);

        assertThat(query, is(notNullValue()));
        assertThat(query.getLanguage(), is(Query.XPATH));
        assertThat(query.getStatement(), is(statement));
    }

    @Test
    public void shouldStoreQueryAsNode() throws Exception {
        String statement = "Some query syntax";

        QueryManager queryManager = workspace.getQueryManager();
        Query query = queryManager.createQuery(statement, Query.XPATH);

        Node node = query.storeAsNode("/storedQuery");
        assertThat(node, is(notNullValue()));
        assertThat(node.getPrimaryNodeType().getName(), is("nt:query"));
        assertThat(node.getProperty("jcr:language").getString(), is(Query.XPATH));
        assertThat(node.getProperty("jcr:statement").getString(), is(statement));
    }

    @Test
    public void shouldLoadStoredQuery() throws Exception {
        String statement = "Some query syntax";

        QueryManager queryManager = workspace.getQueryManager();
        Query query = queryManager.createQuery(statement, Query.XPATH);

        Node node = query.storeAsNode("/storedQuery");

        Query loaded = queryManager.getQuery(node);

        assertThat(loaded, is(notNullValue()));
        assertThat(loaded.getLanguage(), is(Query.XPATH));
        assertThat(loaded.getStatement(), is(statement));
        assertThat(loaded.getStoredQueryPath(), is(node.getPath()));
    }

    @Test
    public void shouldProvideSession() throws Exception {
        assertThat(workspace.getSession(), is(notNullValue()));
    }

    @Test
    public void shouldAllowImportXml() throws Exception {
        String inputData = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                           + "<sv:node xmlns:jcr=\"http://www.jcp.org/jcr/1.0\" xmlns:nt=\"http://www.jcp.org/jcr/nt/1.0\" "
                           + "xmlns:sv=\"http://www.jcp.org/jcr/sv/1.0\" sv:name=\"workspaceTestNode\">"
                           + "<sv:property sv:name=\"jcr:primaryType\" sv:type=\"Name\">"
                           + "<sv:value>nt:unstructured</sv:value></sv:property></sv:node>";
        workspace.importXML("/", new ByteArrayInputStream(inputData.getBytes()), 0);
    }

    @Test( expected = IllegalArgumentException.class )
    public void shouldNotAllowMoveFromNullPath() throws Exception {
        workspace.move(null, null);
    }

    @Test
    public void shouldAllowMoveFromPathToAnotherPathInSameWorkspace() throws Exception {
        workspace.move("/a/b", "/b/b-copy");
    }

    @Test( expected = UnsupportedOperationException.class )
    public void shouldNotAllowRestore() throws Exception {
        workspace.restore(null, false);
    }
}
