package org.sbgrid.api;

import edu.harvard.iq.dataverse.Dataset;
import edu.harvard.iq.dataverse.DatasetVersion;
import edu.harvard.iq.dataverse.Dataverse;
import edu.harvard.iq.dataverse.api.AbstractApiBean;
import edu.harvard.iq.dataverse.api.imports.ImportUtil;
import edu.harvard.iq.dataverse.authorization.Permission;
import edu.harvard.iq.dataverse.authorization.users.User;
import edu.harvard.iq.dataverse.engine.command.DataverseRequest;
import edu.harvard.iq.dataverse.engine.command.impl.CreateDatasetCommand;
import edu.harvard.iq.dataverse.util.json.JsonParseException;
import org.apache.commons.lang.StringUtils;

import javax.ejb.Stateless;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.stream.JsonParsingException;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.StringReader;
import java.sql.Timestamp;
import java.util.Date;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * SBGrid APIs
 * 
 * Additional SBGrid endpoints:
 * 
 * - add datasets with existing DOIs to dataverse
 * - get/set file upload mechanisms
 * 
 * @author bmckinney 
 */
@Stateless
@Path("sbgrid")
public class SBGridResources extends AbstractApiBean {

    private static final Logger LOGGER = Logger.getLogger(SBGridResources.class.getName());
    
    @POST
    @Path("dataverses/{identifier}/datasets")
    @Produces(MediaType.APPLICATION_JSON)
    /**
     * This version of createDataset allows you to import datasets via json with existing DOIs.
     */
    public Response createDataset(String jsonBody, @PathParam("identifier") String alias,
                                   @DefaultValue("new") @QueryParam("importType") String datasetImportType,
                                   @DefaultValue("/") @QueryParam("separator") String datasetSeparator) {
        try {
            User u = findUserOrDie();
            Dataverse dv = findDataverseByAlias(alias);

            // set the import type, default to NEW
            ImportUtil.ImportType it = null;
            if (StringUtils.isNotBlank(datasetImportType)) {
                if (datasetImportType.equalsIgnoreCase("migration")) {
                    it = ImportUtil.ImportType.MIGRATION;
                }
                if (datasetImportType.equalsIgnoreCase("harvest")) {
                    it = ImportUtil.ImportType.HARVEST;
                }
                if (datasetImportType.equalsIgnoreCase("new")) {
                    it = ImportUtil.ImportType.NEW;
                }
            }

            if (!ImportUtil.ImportType.NEW.equals(it)) {
                if (!u.isSuperuser()) {
                    return error(Response.Status.UNAUTHORIZED, 
                            "Attempting to migrate or harvest datasets via this API endpoint is experimental and "
                            + "requires a superuser's API token. Migrating datasets using DDI (XML) is better supported"
                            + "(versions are handled, files are handled, etc.).");
                }
            }

            JsonObject json;
            try ( StringReader rdr = new StringReader(jsonBody) ) {
                json = Json.createReader(rdr).readObject();
            } catch ( JsonParsingException jpe ) {
                LOGGER.log(Level.SEVERE, "Json: {0}", jsonBody);
                return error( Response.Status.BAD_REQUEST, "Error parsing Json: " + jpe.getMessage() );
            }

            Dataset ds = new Dataset();
            ds.setOwner(dv);

            // migration using an existing identifier
            String datasetIdentifier = json.getString("identifier", null);
            String datasetProtocol = json.getString("protocol", null);
            String datasetAuthority = json.getString("authority", null);

            if (StringUtils.isNotBlank(datasetIdentifier) ||
                    StringUtils.isNotBlank(datasetProtocol) ||
                    StringUtils.isNotBlank(datasetAuthority)) {

                // we need all of these parameters to construct a global ID
                if (StringUtils.isNotBlank(datasetIdentifier) &&
                        StringUtils.isNotBlank(datasetProtocol) &&
                        StringUtils.isNotBlank(datasetAuthority)) {

                    // reset import type to MIGRATION when we are sent all of the global id params but no import type
                    if (it != ImportUtil.ImportType.MIGRATION && it != ImportUtil.ImportType.HARVEST) {
                        it = ImportUtil.ImportType.MIGRATION;
                    }

                    // make sure it doesn't already exist
                    String globalId = datasetProtocol + ":" + datasetAuthority + datasetSeparator + datasetIdentifier;
                    if (findDvo(globalId) == null) {
                        ds.setAuthority(datasetAuthority);
                        ds.setProtocol(datasetProtocol);
                        ds.setIdentifier(datasetIdentifier);
                        ds.setGlobalIdCreateTime(new Timestamp(new Date().getTime()));
                    } else {
                        return error(Response.Status.BAD_REQUEST,
                                "A dataset with the identifier " + globalId + " already exists.");
                    }
                } else {
                    return error(Response.Status.BAD_REQUEST,
                            "Protocol, authority and identifier must all be specified when migrating a dataset " +
                                    "with an existing global identifier.");
                }
            }

            JsonObject jsonVersion = json.getJsonObject("datasetVersion");
            if ( jsonVersion == null) {
                return error(Response.Status.BAD_REQUEST, "Json POST data are missing datasetVersion object.");
            }
            try {
                try {
                    DatasetVersion version = new DatasetVersion();
                    version.setDataset(ds);
                    // Use the two argument version so that the version knows which dataset it's associated with.
                    version = jsonParser().parseDatasetVersion(jsonVersion, version);

                    // force "initial version" properties
                    version.setMinorVersionNumber(null);
                    version.setVersionNumber(null);
                    version.setVersionState(DatasetVersion.VersionState.DRAFT);
                    LinkedList<DatasetVersion> versions = new LinkedList<>();
                    versions.add(version);
                    version.setDataset(ds);

                    ds.setVersions( versions );
                } catch (javax.ejb.TransactionRolledbackLocalException rbe) {
                    throw rbe.getCausedByException();
                }
            } catch (JsonParseException ex) {
                LOGGER.log( Level.INFO, "Error parsing dataset version from Json", ex);
                return error(Response.Status.BAD_REQUEST, "Error parsing datasetVersion: " + ex.getMessage());
            } catch ( Exception e ) {
                LOGGER.log( Level.WARNING, "Error parsing dataset version from Json", e);
                return error(Response.Status.INTERNAL_SERVER_ERROR, "Error parsing datasetVersion: " + e.getMessage());
            }

            Dataset managedDs = execCommand(new CreateDatasetCommand(ds, createDataverseRequest(u), false, it));
            return created("/datasets/" + managedDs.getId(),
                    Json.createObjectBuilder().add("id", managedDs.getId()));

        } catch ( WrappedResponse ex ) {
            return ex.getResponse();
        }
    }
    
    @GET
    @Path("dataverses/{identifier}/uploadmechanisms")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listUploadMechanisms(@PathParam("identifier") String alias) {
        try {
            Dataverse dv = findDataverseByAlias(alias);
            JsonArrayBuilder fileUploadMechanismsEnabledArray = Json.createArrayBuilder();
            String fileUploadMechanismsEnabledString = dv.getFileUploadMechanisms();
            if (fileUploadMechanismsEnabledString != null) {
                for (String mech : fileUploadMechanismsEnabledString.split(":")) {
                    fileUploadMechanismsEnabledArray.add(mech);
                }
            }
            return ok(fileUploadMechanismsEnabledArray);
        } catch (WrappedResponse wr) {
            return wr.refineResponse( "Error listing file upload mechanisms for dataverse " + alias + ":");
        }
    }

    @POST
    @Path("dataverses/{identifier}/uploadmechanisms")
    @Produces(MediaType.APPLICATION_JSON)
    public Response setUploadMechanisms(@PathParam("identifier")String alias, String mechs) {
        try {
            Dataverse dv = findDataverseByAlias(alias);
            DataverseRequest req = createDataverseRequest(findAuthenticatedUserOrDie());
            if (permissionSvc.requestOn(req, dv).has(Permission.EditDataverse)) {
                dv.setFileUploadMechanisms(mechs);
                return ok("File upload mechanisms of dataverse " + alias + " updated.");
            } else {
                return error(Response.Status.FORBIDDEN, "Not authorized");
            }
        } catch (WrappedResponse wr) {
            return wr.getResponse();
        }
    }
    
    /**
     * 
     * @param alias
     * @return
     * @throws WrappedResponse
     */
    private Dataverse findDataverseByAlias(String alias) throws WrappedResponse {
        Dataverse dv = dataverseSvc.findByAlias(alias);
        if (dv == null) {
            throw new WrappedResponse(error(Response.Status.NOT_FOUND, 
                    "Can't find dataverse with alias = '" + alias + "'"));
        }
        return dv;
    }

}
