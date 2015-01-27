package org.apache.taverna.scufl2.rdfxml;
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


import static org.apache.taverna.scufl2.rdfxml.RDFXMLReader.APPLICATION_RDF_XML;
import static org.apache.taverna.scufl2.rdfxml.RDFXMLReader.APPLICATION_VND_TAVERNA_SCUFL2_WORKFLOW_BUNDLE;
import static org.apache.taverna.scufl2.rdfxml.RDFXMLWriter.WORKFLOW_BUNDLE_RDF;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;

import org.apache.taverna.scufl2.api.container.WorkflowBundle;
import org.apache.taverna.scufl2.api.core.Workflow;
import org.apache.taverna.scufl2.api.io.ReaderException;
import org.apache.taverna.scufl2.ucfpackage.UCFPackage;
import org.apache.taverna.scufl2.ucfpackage.UCFPackage.ResourceEntry;

import org.apache.taverna.scufl2.rdfxml.jaxb.WorkflowBundleDocument;

public class WorkflowBundleParser extends AbstractParser {

	private WorkflowParser workflowParser;
	private ProfileParser profileParser;

	public WorkflowBundleParser() {
		super();
		workflowParser = new WorkflowParser(parserState);
		profileParser = new ProfileParser(parserState);
	}

	protected String findWorkflowBundlePath() {
		if (APPLICATION_VND_TAVERNA_SCUFL2_WORKFLOW_BUNDLE
				.equals(getParserState().getUcfPackage().getPackageMediaType()))
			for (ResourceEntry potentialRoot : getParserState().getUcfPackage()
					.getRootFiles())
				if (APPLICATION_RDF_XML.equals(potentialRoot.getMediaType()))
					return potentialRoot.getPath();
		return WORKFLOW_BUNDLE_RDF;
	}

	protected WorkflowBundle parseWorkflowBundle(
			org.apache.taverna.scufl2.rdfxml.jaxb.WorkflowBundle wb, URI base)
			throws ReaderException, IOException {
		WorkflowBundle workflowBundle = new WorkflowBundle();
		getParserState().push(workflowBundle);
		try {
			workflowBundle.setResources(getParserState().getUcfPackage());
			if (wb.getName() != null)
				workflowBundle.setName(wb.getName());
			if (wb.getGlobalBaseURI() != null
					&& wb.getGlobalBaseURI().getResource() != null)
				workflowBundle.setGlobalBaseURI(base.resolve(wb
						.getGlobalBaseURI().getResource()));
			mapBean(base.resolve(wb.getAbout()), workflowBundle);
			for (org.apache.taverna.scufl2.rdfxml.jaxb.WorkflowBundle.Workflow wfEntry : wb
					.getWorkflow()) {
				URI wfUri = base.resolve(wfEntry.getWorkflow().getAbout());
				String resource = wfEntry.getWorkflow().getSeeAlso()
						.getResource();
				URI source = uriTools.relativePath(getParserState()
						.getLocation(), base.resolve(resource));
				workflowParser.readWorkflow(wfUri, source);
			}
			for (org.apache.taverna.scufl2.rdfxml.jaxb.WorkflowBundle.Profile pfEntry : wb
					.getProfile()) {
				URI wfUri = base.resolve(pfEntry.getProfile().getAbout());
				String resource = pfEntry.getProfile().getSeeAlso()
						.getResource();
				URI source = uriTools.relativePath(getParserState()
						.getLocation(), base.resolve(resource));
				profileParser.readProfile(wfUri, source);
			}

			if (wb.getMainWorkflow() != null
					&& wb.getMainWorkflow().getResource() != null) {
				URI mainWfUri = base
						.resolve(wb.getMainWorkflow().getResource());
				Workflow mainWorkflow = (Workflow) resolveBeanUri(mainWfUri);
				if (mainWorkflow == null)
					throw new ReaderException("Unknown main workflow "
							+ mainWfUri + ", got"
							+ getParserState().getUriToBean().keySet());
				workflowBundle.setMainWorkflow(mainWorkflow);
			}
			if (wb.getMainProfile() != null
					&& wb.getMainProfile().getResource() != null) {
				URI profileUri = base
						.resolve(wb.getMainProfile().getResource());
				org.apache.taverna.scufl2.api.profiles.Profile mainWorkflow = (org.apache.taverna.scufl2.api.profiles.Profile) resolveBeanUri(profileUri);
				workflowBundle.setMainProfile(mainWorkflow);
			}
		} finally {
			getParserState().pop();
		}
		return workflowBundle;
	}

	@SuppressWarnings("unchecked")
	public WorkflowBundle readWorkflowBundle(UCFPackage ucfPackage,
			URI suggestedLocation) throws IOException, ReaderException {
		try {
			getParserState().setUcfPackage(ucfPackage);
			getParserState().setLocation(suggestedLocation);
			if (getParserState().getLocation() == null) {
				getParserState().setLocation(URI.create(""));
			} else if (!getParserState().getLocation().getRawPath()
					.endsWith("/")) {
				if (getParserState().getLocation().getQuery() != null
						|| getParserState().getLocation().getFragment() != null)
					/*
					 * Ouch.. Perhaps some silly website with ?bundleId=15 ?
					 * We'll better conserve that somehow. Let's do the jar:
					 * trick and hope it works. Have to escape evil chars.
					 */
					getParserState().setLocation(
							URI.create("jar:"
									+ getParserState().getLocation()
									.toASCIIString()
									.replace("?", "%63")
									.replace("#", "#35") + "!/"));
				else
					/*
					 * Simple, pretend we're one level down inside the ZIP file
					 * as a directory
					 */
					getParserState().setLocation(
							getParserState().getLocation().resolve(
									getParserState().getLocation().getRawPath()
											+ "/"));
			}
			String workflowBundlePath = findWorkflowBundlePath();

			InputStream bundleStream = getParserState().getUcfPackage()
					.getResourceAsInputStream(workflowBundlePath);

			JAXBElement<WorkflowBundleDocument> elem;
			try {
				elem = (JAXBElement<WorkflowBundleDocument>) unmarshaller
						.unmarshal(bundleStream);
			} catch (JAXBException e) {
				throw new ReaderException(
						"Can't parse workflow bundle document "
								+ workflowBundlePath, e);
			}
			WorkflowBundleDocument workflowBundleDocument = elem.getValue();

			URI base = getParserState().getLocation().resolve(
					workflowBundlePath);
			if (workflowBundleDocument.getBase() != null)
				base = getParserState().getLocation().resolve(
						workflowBundleDocument.getBase());

			if (workflowBundleDocument.getAny().size() != 1)
				throw new ReaderException(
						"Invalid WorkflowBundleDocument, expected only one <WorkflowBundle>");

			org.apache.taverna.scufl2.rdfxml.jaxb.WorkflowBundle wb = (org.apache.taverna.scufl2.rdfxml.jaxb.WorkflowBundle) workflowBundleDocument
					.getAny().get(0);
			WorkflowBundle workflowBundle = parseWorkflowBundle(wb, base);

			scufl2Tools.setParents(workflowBundle);
			return workflowBundle;
		} finally {
			clearParserState();
		}
	}
}
