package uk.org.taverna.scufl2.api.annotation;

import java.net.URI;
import java.util.Calendar;
import java.util.Set;

import uk.org.taverna.scufl2.api.property.MultiplePropertiesException;
import uk.org.taverna.scufl2.api.property.PropertyLiteral;
import uk.org.taverna.scufl2.api.property.PropertyNotFoundException;
import uk.org.taverna.scufl2.api.property.PropertyResource;
import uk.org.taverna.scufl2.api.property.UnexpectedPropertyException;

public class Revision extends PropertyResource {

	/**
	 * Based on working draft http://www.w3.org/TR/2012/WD-prov-o-20120503/
	 * 
	 * TODO: Update for PROV-O when released as spec
	 **/
	protected static final URI PROV = URI.create("http://www.w3.org/ns/prov#");
	protected static final URI ENTITY = PROV.resolve("#Entity");
	protected static final URI GENERATION = PROV.resolve("#Generation");
	protected static final URI AT_TIME = PROV.resolve("#atTime");
	protected static final URI QUALIFIED_GENERATION = PROV
			.resolve("#qualifiedGeneration");
	protected static final URI WAS_ATTRIBUTED_TO = PROV
			.resolve("#wasAttributedTo");
	protected static final URI WAS_REVISION_OF = PROV.resolve("#wasRevisionOf");

	public Revision() {
		setTypeURI(ENTITY);
	}
	
	public Revision(URI uri, Revision previous) {
		this();
		setResourceURI(uri);
		setPreviousRevision(previous);
	}

	protected Revision(PropertyResource propertyAsResource) {
		setTypeURI(propertyAsResource.getTypeURI());
		setResourceURI(propertyAsResource.getResourceURI());
		setProperties(propertyAsResource.getProperties());
	}

	public void addCreator(URI creator) {
		addPropertyReference(WAS_ATTRIBUTED_TO, creator);
	}

	@Override
	public Revision clone() throws CloneNotSupportedException {
		return new Revision(this);
	}

	public Calendar getCreated() {
		PropertyResource generation;
		try {
			generation = getPropertyAsResource(QUALIFIED_GENERATION);
			return generation.getPropertyAsLiteral(AT_TIME)
					.getLiteralValueAsCalendar();
		} catch (PropertyNotFoundException e) {
			return null;
		} catch (UnexpectedPropertyException e) {
			throw new IllegalStateException(String.format("Invalid %s or %s",
					QUALIFIED_GENERATION, AT_TIME), e);
		} catch (MultiplePropertiesException e) {
			throw new IllegalStateException(String.format("Multiple %s or %s",
					QUALIFIED_GENERATION, AT_TIME), e);
		}
	}

	public Set<URI> getCreators() {
		try {
			return getPropertiesAsResourceURIs(WAS_ATTRIBUTED_TO);
		} catch (UnexpectedPropertyException e) {
			throw new IllegalStateException(String.format("Invalid %s",
					WAS_ATTRIBUTED_TO), e);
		}
	}

	public Revision getPreviousRevision() {
		try {
			PropertyResource propertyAsResource = getPropertyAsResource(WAS_REVISION_OF);
			Revision revision;
			if (propertyAsResource instanceof Revision) {
				revision = (Revision) propertyAsResource;
			} else {			
				revision = new Revision(propertyAsResource);
				// Replace the plain PropertyResource
				setPreviousRevision(revision);
			}			
			return revision;
		} catch (PropertyNotFoundException e) {
			return null;
		} catch (UnexpectedPropertyException e) {
			throw new IllegalStateException(String.format("Invalid %s",
					WAS_REVISION_OF), e);
		} catch (MultiplePropertiesException e) {
			throw new IllegalStateException(String.format("Multiple %s",
					WAS_REVISION_OF), e);
		}
	}

	public void setCreated(Calendar created) {
		PropertyResource generation;
		try {
			generation = getPropertyAsResource(QUALIFIED_GENERATION);
		} catch (PropertyNotFoundException e) {
			generation = addPropertyAsNewResource(QUALIFIED_GENERATION,
					GENERATION);
		} catch (UnexpectedPropertyException e) {
			throw new IllegalStateException(String.format("Invalid %s",
					QUALIFIED_GENERATION), e);
		} catch (MultiplePropertiesException e) {
			throw new IllegalStateException(String.format("Multiple %s",
					QUALIFIED_GENERATION), e);
		}
		generation.clearProperties(AT_TIME);
		generation.addProperty(AT_TIME, new PropertyLiteral(created));
	}

	public void setCreators(Set<URI> creators) {
		clearProperties(WAS_ATTRIBUTED_TO);
		for (URI creator : creators) {
			addCreator(creator);
		}
	}

	public void setPreviousRevision(Revision previous) {
		clearProperties(WAS_REVISION_OF);
		addProperty(WAS_REVISION_OF, previous);
	}

}
