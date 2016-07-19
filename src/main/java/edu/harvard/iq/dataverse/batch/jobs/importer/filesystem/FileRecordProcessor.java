package edu.harvard.iq.dataverse.batch.jobs.importer.filesystem;

import edu.harvard.iq.dataverse.DataFile;
import edu.harvard.iq.dataverse.DataFileServiceBean;
import edu.harvard.iq.dataverse.Dataset;
import edu.harvard.iq.dataverse.DatasetServiceBean;
import edu.harvard.iq.dataverse.DatasetVersion;
import edu.harvard.iq.dataverse.FileMetadata;
import edu.harvard.iq.dataverse.PermissionServiceBean;
import edu.harvard.iq.dataverse.UserServiceBean;
import edu.harvard.iq.dataverse.authorization.Permission;
import edu.harvard.iq.dataverse.authorization.users.AuthenticatedUser;

import javax.annotation.PostConstruct;
import javax.batch.api.chunk.ItemProcessor;
import javax.batch.operations.JobOperator;
import javax.batch.runtime.BatchRuntime;
import javax.batch.runtime.context.JobContext;
import javax.ejb.EJB;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;


@Named
@Dependent
public class FileRecordProcessor implements ItemProcessor {

    private static final Logger logger = Logger.getLogger(FileRecordProcessor.class.getName());

    @Inject
    JobContext jobContext;

    @EJB
    DatasetServiceBean datasetService;

    @EJB
    DataFileServiceBean fileService;

    @EJB
    UserServiceBean userServiceBean;

    @EJB
    PermissionServiceBean permissionServiceBean;

    Dataset dataset;
    AuthenticatedUser user;

    String datasetId;

    long datasetPrimaryKey;
    long userPrimaryKey;

    List<DataFile> dataFileList;

    @PostConstruct
    public void init() {

        JobOperator jobOperator = BatchRuntime.getJobOperator();
        Properties jobParams = jobOperator.getParameters(jobContext.getExecutionId());

        if (jobParams.containsKey("datasetId")) {
            datasetId = jobParams.getProperty("datasetId");
            dataset = datasetService.findByGlobalId(datasetId);
            dataFileList = dataset.getFiles();
        }

        if (jobParams.containsKey("datasetPrimaryKey")) {
            datasetPrimaryKey = Long.parseLong(jobParams.getProperty("datasetPrimaryKey"));
            dataset = datasetService.find(datasetPrimaryKey);
            dataFileList = dataset.getFiles();
        }

        if (jobParams.containsKey("userPrimaryKey")) {
            userPrimaryKey = Long.parseLong(jobParams.getProperty("userPrimaryKey"));
            user = userServiceBean.find(userPrimaryKey);
        }

    }

    @Override
    public Object processItem(Object object) throws Exception {
        String path = object.toString();
        String gid = dataset.getAuthority() + dataset.getDoiSeparator() + dataset.getIdentifier();
        String relativePath = path.substring(path.indexOf(gid) + gid.length() + 1);
        for (DataFile datafile : dataFileList) {
            if (datafile.getStorageIdentifier().equalsIgnoreCase(relativePath)) {
                return null;
            }
        }
        return createDataFile(new File(path));
    }

    private DataFile createDataFile(File file) {

        try {

            if (permissionServiceBean.userOn(user, dataset.getOwner()).has(Permission.AddDataset)) {

                DatasetVersion version = dataset.getLatestVersion();
                String path = file.getAbsolutePath();
                String gid = dataset.getAuthority() + dataset.getDoiSeparator() + dataset.getIdentifier();
                String relativePath = path.substring(path.indexOf(gid) + gid.length() + 1);
                DataFile datafile = new DataFile("application/octet-stream");
                datafile.setStorageIdentifier(relativePath);
                datafile.setFilesize(file.length());
                datafile.setModificationTime(new Timestamp(new Date().getTime()));
                datafile.setCreateDate(new Timestamp(new Date().getTime()));
                datafile.setPermissionModificationTime(new Timestamp(new Date().getTime()));
                datafile.setOwner(dataset);
                datafile.setIngestDone();
                datafile.setmd5("Unknown");

                // set metadata and add to latest version
                FileMetadata fmd = new FileMetadata();
                fmd.setLabel(file.getName());
                fmd.setDataFile(datafile);
                datafile.getFileMetadatas().add(fmd);
                if (version.getFileMetadatas() == null) version.setFileMetadatas(new ArrayList<>());
                version.getFileMetadatas().add(fmd);
                fmd.setDatasetVersion(version);

                datafile = fileService.save(datafile);
                return datafile;

            } else {
                logger.log(Level.SEVERE, "User doesn't have permission to add datasets.");
                return null;
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Exception creating datafile: " + e.getMessage());
            return null;
        }
    }

}
