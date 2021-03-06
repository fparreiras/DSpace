/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.xmlui.aspect.artifactbrowser;

import java.io.IOException;
import java.io.Serializable;
import java.sql.SQLException;
import java.util.Map;
import java.util.HashMap;

import org.apache.cocoon.caching.CacheableProcessingComponent;
import org.apache.cocoon.util.HashUtil;
import org.apache.excalibur.source.SourceValidity;
import org.dspace.app.xmlui.cocoon.AbstractDSpaceTransformer;
import org.dspace.app.xmlui.utils.DSpaceValidity;
import org.dspace.app.xmlui.utils.HandleUtil;
import org.dspace.app.xmlui.utils.UIException;
import org.dspace.app.xmlui.wing.Message;
import org.dspace.app.xmlui.wing.WingException;
import org.dspace.app.xmlui.wing.element.*;
import org.dspace.authorize.AuthorizeException;
import org.dspace.browse.BrowseException;
import org.dspace.browse.BrowseIndex;
import org.dspace.content.Collection;
import org.dspace.content.DSpaceObject;
import org.dspace.core.ConfigurationManager;
import org.xml.sax.SAXException;

/**
 * Display a single collection. This includes a full text search, browse by
 * list, community display and a list of recent submissions.
 * 
 * @author Scott Phillips
 * @author Kevin Van de Velde (kevin at atmire dot com)
 * @author Mark Diggory (markd at atmire dot com)
 * @author Ben Bosman (ben at atmire dot com)
 */
public class CollectionViewer extends AbstractDSpaceTransformer implements CacheableProcessingComponent
{

    /** Language Strings */
    private static final Message T_dspace_home =
        message("xmlui.general.dspace_home");
    

    public static final Message T_untitled = 
    	message("xmlui.general.untitled");
    
    private static final Message T_head_browse =
        message("xmlui.ArtifactBrowser.CollectionViewer.head_browse");
    
    private static final Message T_browse_titles =
        message("xmlui.ArtifactBrowser.CollectionViewer.browse_titles");
    
    private static final Message T_browse_authors =
        message("xmlui.ArtifactBrowser.CollectionViewer.browse_authors");
    
    private static final Message T_browse_dates = 
        message("xmlui.ArtifactBrowser.CollectionViewer.browse_dates");
    

    /** Cached validity object */
    private SourceValidity validity;
    
    /**
     * Generate the unique caching key.
     * This key must be unique inside the space of this component.
     */
    public Serializable getKey()
    {
        try
        {
            DSpaceObject dso = HandleUtil.obtainHandle(objectModel);

            if (dso == null)
            {
                return "0";
            }
                
            return HashUtil.hash(dso.getHandle());
        }
        catch (SQLException sqle)
        {
            // Ignore all errors and just return that the component is not
            // cachable.
            return "0";
        }
    }

    /**
     * Generate the cache validity object.
     * 
     * The validity object will include the collection being viewed and 
     * all recently submitted items. This does not include the community / collection
     * hierarch, when this changes they will not be reflected in the cache.
     */
    public SourceValidity getValidity()
    {
    	if (this.validity == null)
    	{
            Collection collection = null;
	        try
	        {
	            DSpaceObject dso = HandleUtil.obtainHandle(objectModel);
	
	            if (dso == null)
                {
                    return null;
                }
	
	            if (!(dso instanceof Collection))
                {
                    return null;
                }
	
	            collection = (Collection) dso;
	
	            DSpaceValidity validity = new DSpaceValidity();
	            
	            // Add the actual collection;
	            validity.add(collection);
	
	            this.validity = validity.complete();
	        }
	        catch (Exception e)
	        {
	            // Just ignore all errors and return an invalid cache.
	        }

    	}
    	return this.validity;
    }
    
    
    /**
     * Add a page title and trail links.
     */
    public void addPageMeta(PageMeta pageMeta) throws SAXException,
            WingException, UIException, SQLException, IOException,
            AuthorizeException
    {
        DSpaceObject dso = HandleUtil.obtainHandle(objectModel);
        if (!(dso instanceof Collection))
        {
            return;
        }

        Collection collection = (Collection) dso;

        // Set the page title
        String name = collection.getMetadata("name");
        if (name == null || name.length() == 0)
        {
            pageMeta.addMetadata("title").addContent(T_untitled);
        }
        else
        {
            pageMeta.addMetadata("title").addContent(name);
        }

        pageMeta.addTrailLink(contextPath + "/",T_dspace_home);
        HandleUtil.buildHandleTrail(collection,pageMeta,contextPath);
        
        // Add RSS links if available
        String formats = ConfigurationManager.getProperty("webui.feed.formats");
		if ( formats != null )
		{
            String audioCollection = ConfigurationManager.getProperty("webui.feed.podcast.audio.collections");
            String videoCollection = ConfigurationManager.getProperty("webui.feed.podcast.video.collections");

			for (String format : formats.split(","))
			{
				// Remove the protocol number, i.e. just list 'rss' or' atom'
				String[] parts = format.split("_");
				if (parts.length < 1)
                {
                    continue;
                }
				
				String feedFormat = parts[0].trim()+"+xml";
					
				String feedURL = contextPath+"/feed/"+format.trim()+"/"+collection.getHandle();
				pageMeta.addMetadata("feed", feedFormat).addContent(feedURL);

                //if this collection has audio/video specific feeds too. Add them
                if(audioCollection != null && audioCollection.contains(collection.getHandle())) {
                    pageMeta.addMetadata("feed", feedFormat).addContent(feedURL + "/mediaType/audio");
                }

                if(videoCollection != null && videoCollection.contains(collection.getHandle())) {
                    pageMeta.addMetadata("feed", feedFormat).addContent(feedURL + "/mediaType/video");
                }
			}
		}
    }

    /**
     * Display a single collection
     */
    public void addBody(Body body) throws SAXException, WingException,
            UIException, SQLException, IOException, AuthorizeException
    {
        DSpaceObject dso = HandleUtil.obtainHandle(objectModel);
        if (!(dso instanceof Collection))
        {
            return;
        }

        // Set up the major variables
        Collection collection = (Collection) dso;

        // Build the collection viewer division.
        Division home = body.addDivision("collection-home", "primary repository collection");
        String name = collection.getMetadata("name");
        if (name == null || name.length() == 0)
        {
            home.setHead(T_untitled);
        }
        else
        {
            home.setHead(name);
        }

        // The search / browse box.
        {
//            TODO: move browse stuff out of here
            Division search = home.addDivision("collection-search-browse",
                    "secondary search-browse");

            // Browse by list
            Division browseDiv = search.addDivision("collection-browse","secondary browse");
            List browse = browseDiv.addList("collection-browse", List.TYPE_SIMPLE,
                    "collection-browse");
            browse.setHead(T_head_browse);
            String url = contextPath + "/handle/" + collection.getHandle();

            try
            {
                // Get a Map of all the browse tables
                BrowseIndex[] bis = BrowseIndex.getBrowseIndices();
                for (BrowseIndex bix : bis)
                {
                    // Create a Map of the query parameters for this link
                    Map<String, String> queryParams = new HashMap<String, String>();

                    queryParams.put("type", bix.getName());

                    // Add a link to this browse
                    browse.addItemXref(super.generateURL(url + "/browse", queryParams),
                            message("xmlui.ArtifactBrowser.Navigation.browse_" + bix.getName()));
                }
            }
            catch (BrowseException bex)
            {
                browse.addItemXref(url + "/browse?type=title",T_browse_titles);
                browse.addItemXref(url + "/browse?type=author",T_browse_authors);
                browse.addItemXref(url + "/browse?type=dateissued",T_browse_dates);
            }
        }

        // Add the reference
        {
        	Division viewer = home.addDivision("collection-view","secondary");
            ReferenceSet mainInclude = viewer.addReferenceSet("collection-view",
                    ReferenceSet.TYPE_DETAIL_VIEW);
            mainInclude.addReference(collection);
        }

    }
    
    /**
     * Recycle
     */
    public void recycle() 
    {   
        // Clear out our item's cache.
        this.validity = null;
        super.recycle();
    }
}
