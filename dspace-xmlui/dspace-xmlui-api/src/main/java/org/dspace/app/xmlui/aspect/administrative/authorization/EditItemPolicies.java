/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.xmlui.aspect.administrative.authorization;

import org.dspace.app.xmlui.cocoon.AbstractDSpaceTransformer;
import org.dspace.app.xmlui.wing.Message;
import org.dspace.app.xmlui.wing.WingException;
import org.dspace.app.xmlui.wing.element.*;
import org.dspace.authorize.AuthorizeManager;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Item;
import org.dspace.core.Constants;
import org.dspace.eperson.Group;

import java.sql.SQLException;
import java.util.ArrayList;

/**
 * @author Alexey Maslov
 */
public class EditItemPolicies extends AbstractDSpaceTransformer   
{	
	private static final Message T_title = 
		message("xmlui.administrative.authorization.EditItemPolicies.title");
	private static final Message T_policyList_trail =
		message("xmlui.administrative.authorization.general.policyList_trail");
	private static final Message T_authorize_trail =
		message("xmlui.administrative.authorization.general.authorize_trail");
	
	private static final Message T_main_head =
		message("xmlui.administrative.authorization.EditItemPolicies.main_head");
	private static final Message T_main_para1 =
		message("xmlui.administrative.authorization.EditItemPolicies.main_para1");
	private static final Message T_main_para2 =
		message("xmlui.administrative.authorization.EditItemPolicies.main_para2");
	
	private static final Message T_subhead_item =
		message("xmlui.administrative.authorization.EditItemPolicies.subhead_item");
	private static final Message T_subhead_bundle =
		message("xmlui.administrative.authorization.EditItemPolicies.subhead_bundle");
	private static final Message T_subhead_bitstream =
		message("xmlui.administrative.authorization.EditItemPolicies.subhead_bitstream");
	
	private static final Message T_add_itemPolicy_link =
		message("xmlui.administrative.authorization.EditItemPolicies.add_itemPolicy_link");
	private static final Message T_add_bundlePolicy_link =
		message("xmlui.administrative.authorization.EditItemPolicies.add_bundlePolicy_link");
	private static final Message T_add_bitstreamPolicy_link =
		message("xmlui.administrative.authorization.EditItemPolicies.add_bitstreamPolicy_link");
	
	private static final Message T_head_id =
		message("xmlui.administrative.authorization.EditContainerPolicies.head_id");
	private static final Message T_head_action =
		message("xmlui.administrative.authorization.EditContainerPolicies.head_action");
	private static final Message T_head_group =
		message("xmlui.administrative.authorization.EditContainerPolicies.head_group");
	
	private static final Message T_group_edit =
		message("xmlui.administrative.authorization.EditContainerPolicies.group_edit");
	
	private static final Message T_submit_delete =
		message("xmlui.administrative.authorization.EditContainerPolicies.submit_delete");
	private static final Message T_submit_return =
		message("xmlui.general.return");
	
	private static final Message T_no_policies =
		message("xmlui.administrative.authorization.EditContainerPolicies.no_policies");
	
	private static final Message T_dspace_home =
		message("xmlui.general.dspace_home");
	
	
	
	
	public void addPageMeta(PageMeta pageMeta) throws WingException
    {
        pageMeta.addMetadata("title").addContent(T_title);
        pageMeta.addTrailLink(contextPath + "/", T_dspace_home);
        pageMeta.addTrailLink(contextPath + "/admin/authorize", T_authorize_trail);
        pageMeta.addTrail().addContent(T_policyList_trail);
    }
		
	public void addBody(Body body) throws WingException, SQLException 
	{
		/* Get and setup our parameters */
        int itemID = parameters.getParameterAsInteger("itemID",-1);
        int highlightID = parameters.getParameterAsInteger("highlightID",-1);
        String baseURL = contextPath+"/admin/epeople?administrative-continue="+knot.getId();

		/* First, set up our various data structures */
		Item item = Item.find(context, itemID);
		Bundle[] bundles = item.getBundles();
		Bitstream[] bitstreams;
		
		ArrayList<ResourcePolicy> itemPolicies = (ArrayList<ResourcePolicy>)AuthorizeManager.getPolicies(context, item);
		
		// DIVISION: main
		Division main = body.addInteractiveDivision("edit-item-policies",contextPath+"/admin/authorize",Division.METHOD_POST,"primary administrative authorization");
		main.setHead(T_main_head.parameterize(item.getHandle(),item.getID()));
		main.addPara().addHighlight("italic").addContent(T_main_para1);
		main.addPara().addHighlight("italic").addContent(T_main_para2);
		
    	Table table = main.addTable("policies-confirm-delete",itemPolicies.size() + 3, 5);
        Row header = table.addRow(Row.ROLE_HEADER);
        header.addCell();
        header.addCell().addContent(T_head_id);
        header.addCell().addContent(T_head_action);
        header.addCell().addContent(T_head_group);
        header.addCell();

        
        // First, the item's policies are listed
        Row subheader = table.addRow(null,Row.ROLE_HEADER,"subheader");
        subheader.addCell(1, 4).addHighlight("bold").addContent(T_subhead_item);
        subheader.addCell().addHighlight("bold").addXref(baseURL + "&submit_add_item", T_add_itemPolicy_link);
        
        this.rowBuilder(baseURL, table, itemPolicies, item.getID(), Constants.ITEM, highlightID);
    	
    	// Next, one by one, we get the bundles
    	for (Bundle bundle : bundles) {
    		subheader = table.addRow(null,Row.ROLE_HEADER,"subheader");
    		subheader.addCell(null, null, 1, 4, "indent").addHighlight("bold").addContent(T_subhead_bundle.parameterize(bundle.getName(),bundle.getID()));
    		subheader.addCell().addHighlight("bold").addXref(baseURL + "&submit_add_bundle_" + bundle.getID(), T_add_bundlePolicy_link);

    		ArrayList<ResourcePolicy> bundlePolicies = (ArrayList<ResourcePolicy>)AuthorizeManager.getPolicies(context, bundle);
    		this.rowBuilder(baseURL, table, bundlePolicies, bundle.getID(), Constants.BUNDLE, highlightID);
    		
    		// And eventually to the bundle's bitstreams
    		bitstreams = bundle.getBitstreams();
    		for (Bitstream bitstream : bitstreams) {
    			subheader = table.addRow(null,Row.ROLE_HEADER,"subheader");
        		subheader.addCell(null, null, 1, 4, "doubleIndent").addContent(T_subhead_bitstream.parameterize(bitstream.getName(),bitstream.getID()));
        		subheader.addCell().addXref(baseURL + "&submit_add_bitstream_" + bitstream.getID(), T_add_bitstreamPolicy_link);

        		ArrayList<ResourcePolicy> bitstreamPolicies = (ArrayList<ResourcePolicy>)AuthorizeManager.getPolicies(context, bitstream);
        		this.rowBuilder(baseURL, table, bitstreamPolicies, bitstream.getID(), Constants.BITSTREAM, highlightID);    			
    		}
    	}
    	
    	Para buttons = main.addPara();
    	buttons.addButton("submit_delete").setValue(T_submit_delete);
    	buttons.addButton("submit_return").setValue(T_submit_return);
				
		
		main.addHidden("administrative-continue").setValue(knot.getId());
   }
	
	
	private void rowBuilder(String baseURL, Table table, java.util.List<ResourcePolicy> policies, int objectID, int objectType, int highlightID) throws WingException, SQLException 
	{
		// If the list of policies is empty, say so
		if (policies == null || policies.size() == 0) {
			table.addRow().addCell(1, 4).addHighlight("italic").addContent(T_no_policies);
		}
		// Otherwise, iterate over the given policies, creating a new table row for each one
		else {
			for (ResourcePolicy policy : policies) 
	    	{
				Row row;
				if (policy.getID() == highlightID)
                {
                    row = table.addRow(null, null, "highlight");
                }
				else
                {
                    row = table.addRow();
                }
				
				Cell cell;
				if (objectType == Constants.BUNDLE)
                {
                    cell = row.addCell(null, null, "indent");
                }
				else if (objectType == Constants.BITSTREAM)
                {
                    cell = row.addCell(null, null, "doubleIndent");
                }
				else
                {
                    cell = row.addCell();
                }
	    		
				
	    		CheckBox select = cell.addCheckBox("select_policy");
	    		
	        	select.setLabel(String.valueOf(policy.getID()));
	        	select.addOption(String.valueOf(policy.getID()));
	        	
	        	// Accounting for the funky case of an empty policy
	        	Group policyGroup = policy.getGroup();
	        	
	        	row.addCell().addXref(baseURL + "&submit_edit&policy_id=" + policy.getID() + 
	        			"&object_id=" + objectID + "&object_type=" + objectType, String.valueOf(policy.getID()));
	        	row.addCell().addXref(baseURL + "&submit_edit&policy_id=" + policy.getID() + 
	        			"&object_id=" + objectID + "&object_type=" + objectType, policy.getActionText());
	        	if (policyGroup != null) {
	        		Cell groupCell = row.addCell(1,2);
	        		groupCell.addContent(policyGroup.getName());
	        		Highlight groupHigh = groupCell.addHighlight("small");
	        		groupHigh.addContent(" [");
	        		groupHigh.addXref(baseURL + "&submit_edit_group&group_id=" + policyGroup.getID(), T_group_edit);
	        		groupHigh.addContent("]");
	        	}
	        	else {
	            	row.addCell(1,2).addContent("...");
	        	}
		    }
		}
	}
	
	
}
