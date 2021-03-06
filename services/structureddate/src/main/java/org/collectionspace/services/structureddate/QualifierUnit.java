package org.collectionspace.services.structureddate;

public enum QualifierUnit {
	DAYS   ("urn:cspace:core.collectionspace.org:vocabularies:name(datequalifier):item:name(days)'Day(s)'"),
	MONTHS ("urn:cspace:core.collectionspace.org:vocabularies:name(datequalifier):item:name(month)'Month(s)'"),
	YEARS  ("urn:cspace:core.collectionspace.org:vocabularies:name(datequalifier):item:name(years)'Year(s)'");
	
	private final String value;
	
	private QualifierUnit(String value) {
		this.value = value;
	}
	
	public String toString() {
		return value;
	}
}
