/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.submit.step;

import org.apache.log4j.Logger;
import org.dspace.app.util.SubmissionInfo;
import org.dspace.app.util.Util;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.AuthorizeManager;
import org.dspace.content.*;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.eperson.Group;
import org.dspace.handle.HandleManager;
import org.dspace.submit.AbstractProcessingStep;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.logging.Level;

/**
 * Upload step for DSpace. Processes the actual upload of files
 * for an item being submitted into DSpace.
 * <P>
 * This class performs all the behind-the-scenes processing that
 * this particular step requires.  This class's methods are utilized
 * by both the JSP-UI and the Manakin XML-UI
 *
 * @see org.dspace.app.util.SubmissionConfig
 * @see org.dspace.app.util.SubmissionStepConfig
 * @see org.dspace.submit.AbstractProcessingStep
 *
 * @author Tim Donohue
 * @version $Revision: 4777 $
 */
public class UploadLicenseStep extends AbstractProcessingStep
{
    /** Button to upload a file * */
    public static final String SUBMIT_UPLOAD_BUTTON = "submit_upload";

    /** Button to skip uploading a file * */
    public static final String SUBMIT_SKIP_BUTTON = "submit_skip";

    /** Button to submit more files * */
    public static final String SUBMIT_MORE_BUTTON = "submit_more";

    /** Button to cancel editing of file info * */
    public static final String CANCEL_EDIT_BUTTON = "submit_edit_cancel";

    /***************************************************************************
     * STATUS / ERROR FLAGS (returned by doProcessing() if an error occurs or
     * additional user interaction may be required)
     *
     * (Do NOT use status of 0, since it corresponds to STATUS_COMPLETE flag
     * defined in the JSPStepManager class)
     **************************************************************************/
    // integrity error occurred
    public static final int STATUS_INTEGRITY_ERROR = 1;

    // error in uploading file
    public static final int STATUS_UPLOAD_ERROR = 2;

    // error - no files uploaded!
    public static final int STATUS_NO_FILES_ERROR = 5;

    // format of uploaded file is unknown
    public static final int STATUS_UNKNOWN_FORMAT = 10;

    // edit file information
    public static final int STATUS_EDIT_BITSTREAM = 20;

    // return from editing file information
    public static final int STATUS_EDIT_COMPLETE = 25;

    /** log4j logger */
    private static Logger log = Logger.getLogger(UploadLicenseStep.class);

    /** is the upload required? */
    public static boolean fileRequired = false;   //This step is skippable.

    public static final String PROXY_LICENSE_BUNDLE_NAME = "PROXY-LICENSE";

    /**
     * Do any processing of the information input by the user, and/or perform
     * step processing (if no user interaction required)
     * <P>
     * It is this method's job to save any data to the underlying database, as
     * necessary, and return error messages (if any) which can then be processed
     * by the appropriate user interface (JSP-UI or XML-UI)
     * <P>
     * NOTE: If this step is a non-interactive step (i.e. requires no UI), then
     * it should perform *all* of its processing in this method!
     *
     * @param context
     *            current DSpace context
     * @param request
     *            current servlet request object
     * @param response
     *            current servlet response object
     * @param subInfo
     *            submission info object
     * @return Status or error flag which will be processed by
     *         doPostProcessing() below! (if STATUS_COMPLETE or 0 is returned,
     *         no errors occurred!)
     */
    public int doProcessing(Context context, HttpServletRequest request,
            HttpServletResponse response, SubmissionInfo subInfo)
            throws ServletException, IOException, SQLException,
            AuthorizeException
    {
        // get button user pressed
        String buttonPressed = Util.getSubmitButton(request, NEXT_BUTTON);

        // get reference to item
        Item item = subInfo.getSubmissionItem().getItem();

        // -----------------------------------
        // Step #0: Upload new files (if any)
        // -----------------------------------
        String contentType = request.getContentType();

        // if multipart form, then we are uploading a file
        if ((contentType != null)
                && (contentType.indexOf("multipart/form-data") != -1))
        {
            // This is a multipart request, so it's a file upload
            // (return any status messages or errors reported)
            int status = processUploadFile(context, request, response, subInfo);

            // if error occurred, return immediately
            if (status != STATUS_COMPLETE)
            {
                return status;
            }
        }

        // if user pressed jump-to button in process bar,
        // return success (so that jump will occur)
        if (buttonPressed.startsWith(PROGRESS_BAR_PREFIX))
        {
            //TODO will not work
            // check if a file is required to be uploaded
            if (fileRequired && !item.hasUploadedFiles())
            {
                return STATUS_NO_FILES_ERROR;
            }
            else
            {
                return STATUS_COMPLETE;
            }
        }

        // ---------------------------------------------
        // Step #1: Check if this was just a request to
        // edit file information.
        // (or canceled editing information)
        // ---------------------------------------------
        // check if we're already editing a specific bitstream
        if (request.getParameter("bitstream_id") != null)
        {
            if (buttonPressed.equals(CANCEL_EDIT_BUTTON))
            {
                // canceled an edit bitstream request
                subInfo.setBitstream(null);

                // this flag will just return us to the normal upload screen
                return STATUS_EDIT_COMPLETE;
            }
            else
            {
                // load info for bitstream we are editing
                Bitstream b = Bitstream.find(context, Integer.parseInt(request
                        .getParameter("bitstream_id")));

                // save bitstream to submission info
                subInfo.setBitstream(b);
            }
        }
        else if (buttonPressed.startsWith("submit_edit_"))
        {
            // get ID of bitstream that was requested for editing
            String bitstreamID = buttonPressed.substring("submit_edit_"
                    .length());

            Bitstream b = Bitstream
                    .find(context, Integer.parseInt(bitstreamID));

            // save bitstream to submission info
            subInfo.setBitstream(b);

            // return appropriate status flag to say we are now editing the
            // bitstream
            return STATUS_EDIT_BITSTREAM;
        }

        // ---------------------------------------------
        // Step #2: Process any remove file request(s)
        // ---------------------------------------------
        // Remove-selected requests come from Manakin
        if (buttonPressed.equalsIgnoreCase("submit_remove_selected"))
        {
            // this is a remove multiple request!

            if (request.getParameter("remove") != null)
            {
                // get all files to be removed
                String[] removeIDs = request.getParameterValues("remove");

                // remove each file in the list
                for (int i = 0; i < removeIDs.length; i++)
                {
                    int id = Integer.parseInt(removeIDs[i]);

                    int status = processRemoveFile(context, item, id);

                    // if error occurred, return immediately
                    if (status != STATUS_COMPLETE)
                    {
                        return status;
                    }
                }

                // remove current bitstream from Submission Info
                subInfo.setBitstream(null);
            }
        }
        else if (buttonPressed.startsWith("submit_remove_"))
        {
            // A single file "remove" button must have been pressed

            int id = Integer.parseInt(buttonPressed.substring(14));
            int status = processRemoveFile(context, item, id);

            // if error occurred, return immediately
            if (status != STATUS_COMPLETE)
            {
                return status;
            }

            // remove current bitstream from Submission Info
            subInfo.setBitstream(null);
        }

        // -------------------------------------------------
        // Step #3: Check for a change in file description
        // -------------------------------------------------
        String fileDescription = request.getParameter("description");

        if (fileDescription != null && fileDescription.length() > 0)
        {
            // save this file description
            int status = processSaveFileDescription(context, request, response,
                    subInfo);

            // if error occurred, return immediately
            if (status != STATUS_COMPLETE)
            {
                return status;
            }
        }

        // ------------------------------------------
        // Step #4: Check for a file format change
        // (if user had to manually specify format)
        // ------------------------------------------
        int formatTypeID = Util.getIntParameter(request, "format");
        String formatDesc = request.getParameter("format_description");

        // if a format id or description was found, then save this format!
        if (formatTypeID >= 0
                || (formatDesc != null && formatDesc.length() > 0))
        {
            // save this specified format
            int status = processSaveFileFormat(context, request, response,
                    subInfo);

            // if error occurred, return immediately
            if (status != STATUS_COMPLETE)
            {
                return status;
            }
        }


        // ---------------------------------------------------
        // Step #6: Determine if there is an error because no
        // files have been uploaded.
        // ---------------------------------------------------
        //check if a file is required to be uploaded
        // User should be allowed to skip this step.

        if (fileRequired && !item.hasUploadedFiles())
        {
            return STATUS_NO_FILES_ERROR;
        }

        // commit all changes to database
        context.commit();

        return STATUS_COMPLETE;
    }

    /**
     * Determine if the Upload License Step is visible to the user. Currently
     * the step is only available to super-admins and collection admins.
     *
     * Retrieves the number of pages that this "step" extends over. This method
     * is used to build the progress bar.
     * <P>
     * This method may just return 1 for most steps (since most steps consist of
     * a single page). But, it should return a number greater than 1 for any
     * "step" which spans across a number of HTML pages. For example, the
     * configurable "Describe" step (configured using input-forms.xml) overrides
     * this method to return the number of pages that are defined by its
     * configuration file.
     * <P>
     * Steps which are non-interactive (i.e. they do not display an interface to
     * the user) should return a value of 1, so that they are only processed
     * once!
     *
     * @param request
     *            The HTTP Request
     * @param subInfo
     *            The current submission information object
     *
     * @return the number of pages in this step
     */
    public int getNumberOfPages(HttpServletRequest request,
            SubmissionInfo subInfo) throws ServletException
    {
        
        //Allow for configurable way to disabled proxy-license submission
        

        try {
            Context context = subInfo.getContext();
            Collection submittedCollection = (Collection) HandleManager.resolveToObject(context, subInfo.getCollectionHandle());
            Group adminGroup = submittedCollection.getAdministrators();
            Boolean isCollAdmin = false;

            Boolean allowCollectionAdmin = ConfigurationManager.getBooleanProperty("submit.proxy-license.collection-admin-submit", true);

            if(adminGroup != null)
            {
                if(allowCollectionAdmin)
                {
                    isCollAdmin = adminGroup.isMember(context.getCurrentUser());
                } else {
                    isCollAdmin = false;
                }
            }

            if (isCollAdmin || AuthorizeManager.isAdmin(context))
            {
                return 1;
            } else
            {
                return STATUS_COMPLETE;
            }
        } catch (SQLException ex) {
            java.util.logging.Logger.getLogger(UploadLicenseStep.class.getName()).log(Level.SEVERE, null, ex);
            return 0;
        }
        //return 0;
    }

    // ****************************************************************
    // ****************************************************************
    // METHODS FOR UPLOADING FILES (and associated information)
    // ****************************************************************
    // ****************************************************************

    /**
     * Remove a file from an item
     *
     * @param context
     *            current DSpace context
     * @param item
     *            Item where file should be removed from
     * @param bitstreamID
     *            The id of bitstream representing the file to remove
     * @return Status or error flag which will be processed by
     *         UI-related code! (if STATUS_COMPLETE or 0 is returned,
     *         no errors occurred!)
     */
    protected int processRemoveFile(Context context, Item item, int bitstreamID)
            throws IOException, SQLException, AuthorizeException
    {

        Bitstream bitstream;

        // Try to find bitstream
        try
        {
            bitstream = Bitstream.find(context, bitstreamID);
        }
        catch (NumberFormatException nfe)
        {
            bitstream = null;
        }

        if (bitstream == null)
        {
            // Invalid or mangled bitstream ID
            // throw an error and return immediately
            return STATUS_INTEGRITY_ERROR;
        }

        // remove bitstream from bundle..
        // delete bundle if it's now empty
        Bundle[] bundles = bitstream.getBundles();

        bundles[0].removeBitstream(bitstream);

        Bitstream[] bitstreams = bundles[0].getBitstreams();

        // remove bundle if it's now empty
        if (bitstreams.length < 1)
        {
            item.removeBundle(bundles[0]);
            item.update();
        }

        // no errors occurred
        return STATUS_COMPLETE;
    }

    /**
     * Process the upload of a new file!
     *
     * @param context
     *            current DSpace context
     * @param request
     *            current servlet request object
     * @param response
     *            current servlet response object
     * @param subInfo
     *            submission info object
     *
     * @return Status or error flag which will be processed by
     *         UI-related code! (if STATUS_COMPLETE or 0 is returned,
     *         no errors occurred!)
     */
    protected int processUploadFile(Context context, HttpServletRequest request,
            HttpServletResponse response, SubmissionInfo subInfo)
            throws ServletException, IOException, SQLException,
            AuthorizeException
    {

        boolean formatKnown = true;
        boolean fileOK = false;
        BitstreamFormat bf = null;
        Bitstream b = null;

        //NOTE: File should already be uploaded.
        //Manakin does this automatically via Cocoon.
        //For JSP-UI, the SubmissionController.uploadFiles() does the actual upload

        Enumeration attNames = request.getAttributeNames();

        //loop through our request attributes
        while(attNames.hasMoreElements())
        {
            String attr = (String) attNames.nextElement();

            //if this ends with "-path", this attribute
            //represents a newly uploaded file
            if(attr.endsWith("-path"))
            {
                //strip off the -path to get the actual parameter
                //that the file was uploaded as
                String param = attr.replace("-path", "");

                // Load the file's path and input stream and description
                String filePath = (String) request.getAttribute(param + "-path");
                InputStream fileInputStream = (InputStream) request
                                    .getAttribute(param + "-inputstream");

                //attempt to get description from attribute first, then direct from a parameter
                String fileDescription =  (String) request.getAttribute(param + "-description");
                if(fileDescription==null ||fileDescription.length()==0)
                {
                    fileDescription = request.getParameter("description");
                }

                // if information wasn't passed by User Interface, we had a problem
                // with the upload
                if (filePath == null || fileInputStream == null)
                    return STATUS_UPLOAD_ERROR;

                if (subInfo == null)
                {
                    return STATUS_INTEGRITY_ERROR;
                }

                // Create the bitstream
                Item item = subInfo.getSubmissionItem().getItem();

                // do we already have a bundle?
                Bundle[] bundles = item.getBundles(PROXY_LICENSE_BUNDLE_NAME);

                if (bundles.length < 1)
                {
                    // set bundle's name to LICENSE
                    b = item.createSingleBitstream(fileInputStream, PROXY_LICENSE_BUNDLE_NAME);
                }
                else
                {
                    // we have a bundle already, just add bitstream
                    b = bundles[0].createBitstream(fileInputStream);
                }

                // Strip all but the last filename. It would be nice
                // to know which OS the file came from.
                String noPath = filePath;

                while (noPath.indexOf('/') > -1)
                {
                    noPath = noPath.substring(noPath.indexOf('/') + 1);
                }

                while (noPath.indexOf('\\') > -1)
                {
                    noPath = noPath.substring(noPath.indexOf('\\') + 1);
                }

                b.setName(noPath);
                b.setSource(filePath);
                b.setDescription(fileDescription);

                // Identify the format
                bf = FormatIdentifier.guessFormat(context, b);
                b.setFormat(bf);

                // Update to DB
                b.update();
                item.update();

                if ((bf != null) && (bf.isInternal()))
                {
                    log.warn("Attempt to upload file format marked as internal system use only");
                    backoutBitstream(subInfo, b, item);
                    return STATUS_UPLOAD_ERROR;
                }

                // Check for virus - TODO

                // If we got this far then everything is more or less ok.

                // Comment - not sure if this is the right place for a commit here
                // but I'm not brave enough to remove it - Robin.
                context.commit();

                // save this bitstream to the submission info, as the
                // bitstream we're currently working with
                subInfo.setBitstream(b);

                //if format was not identified
                if (bf == null)
                {
                    return STATUS_UNKNOWN_FORMAT;
                }

            }//end if attribute ends with "-path"
        }//end while

        return STATUS_COMPLETE;

    }

    /*
     If we created a new Bitstream but now realised there is a problem then remove it.
    */
    private void backoutBitstream(SubmissionInfo subInfo, Bitstream b, Item item) throws SQLException, AuthorizeException, IOException
    {
        // remove bitstream from bundle..
        // delete bundle if it's now empty
        Bundle[] bnd = b.getBundles();

        bnd[0].removeBitstream(b);

        Bitstream[] bitstreams = bnd[0].getBitstreams();

        // remove bundle if it's now empty
        if (bitstreams.length < 1)
        {
            item.removeBundle(bnd[0]);
            item.update();
        }

        subInfo.setBitstream(null);
    }

    /**
     * Process input from get file type page
     *
     * @param context
     *            current DSpace context
     * @param request
     *            current servlet request object
     * @param response
     *            current servlet response object
     * @param subInfo
     *            submission info object
     *
     * @return Status or error flag which will be processed by
     *         UI-related code! (if STATUS_COMPLETE or 0 is returned,
     *         no errors occurred!)
     */
    protected int processSaveFileFormat(Context context,
                                        HttpServletRequest request, HttpServletResponse response,
                                        SubmissionInfo subInfo) throws ServletException, IOException,
            SQLException, AuthorizeException
    {
        if (subInfo.getBitstream() != null)
        {
            // Did the user select a format?
            int typeID = Util.getIntParameter(request, "format");

            BitstreamFormat format = BitstreamFormat.find(context, typeID);

            if (format != null)
            {
                subInfo.getBitstream().setFormat(format);
            }
            else
            {
                String userDesc = request.getParameter("format_description");

                subInfo.getBitstream().setUserFormatDescription(userDesc);
            }

            // update database
            subInfo.getBitstream().update();
        }
        else
        {
            return STATUS_INTEGRITY_ERROR;
        }

        return STATUS_COMPLETE;
    }

    /**
     * Process input from the "change file description" page
     *
     * @param context
     *            current DSpace context
     * @param request
     *            current servlet request object
     * @param response
     *            current servlet response object
     * @param subInfo
     *            submission info object
     *
     * @return Status or error flag which will be processed by
     *         UI-related code! (if STATUS_COMPLETE or 0 is returned,
     *         no errors occurred!)
     */
    protected int processSaveFileDescription(Context context,
                                             HttpServletRequest request, HttpServletResponse response,
                                             SubmissionInfo subInfo) throws ServletException, IOException,
            SQLException, AuthorizeException
    {
        if (subInfo.getBitstream() != null)
        {
            subInfo.getBitstream().setDescription(
                    request.getParameter("description"));
            subInfo.getBitstream().update();

            context.commit();
        }
        else
        {
            return STATUS_INTEGRITY_ERROR;
        }

        return STATUS_COMPLETE;
    }

}

