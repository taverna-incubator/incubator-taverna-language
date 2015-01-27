package org.apache.taverna.scufl2.wfdesc;
/*
 *
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
 *
*/


import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.taverna.scufl2.api.container.WorkflowBundle;
import org.apache.taverna.scufl2.api.io.ReaderException;
import org.apache.taverna.scufl2.api.io.WorkflowBundleIO;
import org.apache.taverna.scufl2.api.io.WriterException;
import org.apache.taverna.scufl2.wfdesc.ROEvoSerializer;
import org.junit.Before;
import org.junit.Test;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.QueryLanguage;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFParseException;
import org.openrdf.sail.memory.MemoryStore;


public class TestLocalDependency {
	private static final String LOCALDEPENDENCY = "localdependency.t2flow";
	
	ROEvoSerializer roEvo = new ROEvoSerializer();
	WorkflowBundleIO io = new WorkflowBundleIO();

	private WorkflowBundle localDependency;

	private ByteArrayOutputStream output = new ByteArrayOutputStream();
	
	@Before
	public void loadDepdenency() throws ReaderException, IOException, WriterException, RDFParseException, RepositoryException, QueryEvaluationException, MalformedQueryException {
		InputStream localStream = getClass().getResourceAsStream("/" + LOCALDEPENDENCY);
		assertNotNull(localStream);
		localDependency = io.readBundle(localStream, "application/vnd.taverna.t2flow+xml");
		assertNotNull(localDependency);
	}
	
	
	@Test
	public void wfdesc() throws Exception {		
 		io.writeBundle(localDependency, output, "text/vnd.wf4ever.wfdesc+turtle");
 		
		Repository myRepository = new SailRepository(new MemoryStore());
		myRepository.initialize();
		RepositoryConnection con = myRepository.getConnection();
		String root = "app://f0b5fb9c-b180-45b3-afb4-8d70bbb27190/";
		//System.out.write(output.toByteArray());
		con.add(new ByteArrayInputStream(output.toByteArray()), root, RDFFormat.TURTLE);
		
		assertTrue(con.prepareBooleanQuery(QueryLanguage.SPARQL, 
				"PREFIX wfdesc: <http://purl.org/wf4ever/wfdesc#>  " +
				"PREFIX wf4ever: <http://purl.org/wf4ever/wf4ever#>  " +
				"PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>  " +
				"PREFIX roterms: <http://purl.org/wf4ever/roterms#>  " +
				"ASK { " +
				"?wf a wfdesc:Workflow ;" +
				"  wfdesc:hasSubProcess ?beanshell . " +
				"?beanshell a wfdesc:Process, wf4ever:BeanshellScript ;" +
				"  wf4ever:script ?script ; " +
				"  roterms:requiresSoftware ?sw . " +
				"?sw rdfs:label \"hello.jar\" ; " +
                "    rdfs:comment \"JAR dependency\" ." +
			    "}").evaluate());
		
	}
	
}
