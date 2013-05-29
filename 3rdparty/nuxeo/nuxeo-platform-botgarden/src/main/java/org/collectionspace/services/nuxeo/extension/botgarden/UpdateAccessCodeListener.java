package org.collectionspace.services.nuxeo.extension.botgarden;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.collectionspace.services.batch.nuxeo.UpdateAccessCodeBatchJob;
import org.collectionspace.services.client.workflow.WorkflowClient;
import org.collectionspace.services.collectionobject.nuxeo.CollectionObjectConstants;
import org.collectionspace.services.common.ResourceMap;
import org.collectionspace.services.common.invocable.InvocationResults;
import org.collectionspace.services.taxonomy.nuxeo.TaxonConstants;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.event.CoreEventConstants;
import org.nuxeo.ecm.core.api.event.DocumentEventTypes;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;

public class UpdateAccessCodeListener implements EventListener {
	final Log logger = LogFactory.getLog(UpdateAccessCodeListener.class);

	public static final String PREVIOUS_DEAD_FLAG_PROPERTY_NAME = "UpdateAccessCodeListener.previousDeadFlag";
	public static final String PREVIOUS_ACCESS_CODE_PROPERTY_NAME = "UpdateAccessCodeListener.previousAccessCode";
	
	/* 
	 * Set the access code on taxon records when the dead flag on a referencing collectionobject 
	 * changes, or the access code on a child taxon changes.
	 */
	public void handleEvent(Event event) throws ClientException {
		EventContext ec = event.getContext();
		
		if (ec instanceof DocumentEventContext) {
			DocumentEventContext context = (DocumentEventContext) ec;
			DocumentModel doc = context.getSourceDocument();

			logger.debug("docType=" + doc.getType());

			if (doc.getType().startsWith(CollectionObjectConstants.NUXEO_DOCTYPE) &&
					!doc.isVersion() && 
					!doc.isProxy() && 
					!doc.getCurrentLifeCycleState().equals(WorkflowClient.WORKFLOWSTATE_DELETED)) {
				
				if (event.getName().equals(DocumentEventTypes.BEFORE_DOC_UPDATE)) {
					// Stash the previous dead flag value, so it can be retrieved in the documentModified handler.
					
					DocumentModel previousDoc = (DocumentModel) context.getProperty(CoreEventConstants.PREVIOUS_DOCUMENT_MODEL);
					String previousDeadFlag = (String) previousDoc.getProperty(CollectionObjectConstants.DEAD_FLAG_SCHEMA_NAME, CollectionObjectConstants.DEAD_FLAG_FIELD_NAME);

					context.setProperty(PREVIOUS_DEAD_FLAG_PROPERTY_NAME, previousDeadFlag);
				}
				else {
					boolean updateRequired = false;
					
					if (event.getName().equals(DocumentEventTypes.DOCUMENT_UPDATED)) {						
						 // As an optimization, check if the dead flag of the collectionobject has
						 // changed, or if the taxonomic identification has changed. If so, we need to
						 // update the access codes of referenced taxon records.

						String previousDeadFlag = (String) context.getProperty(PREVIOUS_DEAD_FLAG_PROPERTY_NAME);
						String currentDeadFlag = (String) doc.getProperty(CollectionObjectConstants.DEAD_FLAG_SCHEMA_NAME, CollectionObjectConstants.DEAD_FLAG_FIELD_NAME);

						if (previousDeadFlag == null) {
							previousDeadFlag = "";
						}
						
						if (currentDeadFlag == null) {
							currentDeadFlag = "";
						}
						
						if (previousDeadFlag.equals(currentDeadFlag)) {
							logger.debug("update not required: previousDeadFlag=" + previousDeadFlag + " currentDeadFlag=" + currentDeadFlag);
						}
						else {
							logger.debug("update required: previousDeadFlag=" + previousDeadFlag + " currentDeadFlag=" + currentDeadFlag);
							updateRequired = true;
						}						
					}
					else if (event.getName().equals(DocumentEventTypes.DOCUMENT_CREATED)) {
						updateRequired = true;
					}
					
					if (updateRequired) {
						String collectionObjectCsid = doc.getName();
						
						try {
							// Pass false for the second parameter to updateReferencedAccessCodes, so that it doesn't
						 	// propagate changes up the taxon hierarchy. Propagation will be taken care of by this
							// event handler. 
						
							InvocationResults results = createUpdater().updateReferencedAccessCodes(collectionObjectCsid, false);
			
							logger.debug("updateReferencedAccessCodes complete: numAffected=" + results.getNumAffected() + " userNote=" + results.getUserNote());
						}
						catch (Exception e) {
							logger.error(e.getMessage(), e);
						}
					}
				}
			}
			else if (doc.getType().startsWith(TaxonConstants.NUXEO_DOCTYPE) &&
					!doc.isVersion() && 
					!doc.isProxy() && 
					!doc.getCurrentLifeCycleState().equals(WorkflowClient.WORKFLOWSTATE_DELETED)) {
				
				if (event.getName().equals(DocumentEventTypes.BEFORE_DOC_UPDATE)) {					
					// Stash the previous access code value, so it can be retrieved in the documentModified handler.

					DocumentModel previousDoc = (DocumentModel) context.getProperty(CoreEventConstants.PREVIOUS_DOCUMENT_MODEL);
					String previousAccessCode = (String) previousDoc.getProperty(TaxonConstants.ACCESS_CODE_SCHEMA_NAME, TaxonConstants.ACCESS_CODE_FIELD_NAME);

					context.setProperty(PREVIOUS_ACCESS_CODE_PROPERTY_NAME, previousAccessCode);
				}
				else {
					boolean updateRequired = false;

					if (event.getName().equals(DocumentEventTypes.DOCUMENT_UPDATED)) {				
						// As an optimization, check if the access code of the taxon has
						// changed. We only need to update the access code of the parent taxon
						// record if it has.

						String previousAccessCode = (String) context.getProperty(PREVIOUS_ACCESS_CODE_PROPERTY_NAME);
						String currentAccessCode = (String) doc.getProperty(TaxonConstants.ACCESS_CODE_SCHEMA_NAME, TaxonConstants.ACCESS_CODE_FIELD_NAME);

						if (previousAccessCode == null) {
							previousAccessCode = "";
						}
						
						if (currentAccessCode == null) {
							currentAccessCode = "";
						}
						
						if (previousAccessCode.equals(currentAccessCode)) {
							logger.debug("update not required: previousAccessCode=" + previousAccessCode + " currentAccessCode=" + currentAccessCode);
						}
						else {
							logger.debug("update required: previousAccessCode=" + previousAccessCode + " currentAccessCode=" + currentAccessCode);
							updateRequired = true;
						}
					}
					else if (event.getName().equals(DocumentEventTypes.DOCUMENT_CREATED)) {
						updateRequired = true;	
					}
				
					if (updateRequired) {
						String taxonCsid = doc.getName();
						
						try {
							// Pass false for the second parameter to updateParentAccessCode, so that it doesn't
						 	// propagate changes up the taxon hierarchy. Propagation will be taken care of by this
							// event handler.
							
							InvocationResults results = createUpdater().updateParentAccessCode(taxonCsid, false);
			
							logger.debug("updateParentAccessCode complete: numAffected=" + results.getNumAffected() + " userNote=" + results.getUserNote());
						}
						catch (Exception e) {
							logger.error(e.getMessage(), e);
						}
					}
				}
			}
		}
	}
	
	private UpdateAccessCodeBatchJob createUpdater() {
		ResourceMap resourceMap = ResteasyProviderFactory.getContextData(ResourceMap.class);

		UpdateAccessCodeBatchJob updater = new UpdateAccessCodeBatchJob();
		updater.setResourceMap(resourceMap);

		return updater;
	}
}