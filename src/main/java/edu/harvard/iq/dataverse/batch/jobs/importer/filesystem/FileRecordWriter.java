package edu.harvard.iq.dataverse.batch.jobs.importer.filesystem;

import edu.harvard.iq.dataverse.DataFile;
import edu.harvard.iq.dataverse.DataFileServiceBean;
import edu.harvard.iq.dataverse.Dataset;
import edu.harvard.iq.dataverse.DatasetServiceBean;
import edu.harvard.iq.dataverse.PermissionServiceBean;
import edu.harvard.iq.dataverse.UserServiceBean;
import edu.harvard.iq.dataverse.authorization.Permission;
import edu.harvard.iq.dataverse.authorization.users.AuthenticatedUser;

import javax.batch.api.chunk.AbstractItemWriter;
import javax.batch.operations.JobOperator;
import javax.batch.runtime.BatchRuntime;
import javax.batch.runtime.context.JobContext;
import javax.ejb.EJB;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;


@Named
@Dependent
public class FileRecordWriter extends AbstractItemWriter {


    private static final Logger logger = Logger.getLogger(FileRecordWriter.class.getName());

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


    @Override
    public void open(Serializable checkpoint) throws Exception {
        JobOperator jobOperator = BatchRuntime.getJobOperator();
        Properties jobParams = jobOperator.getParameters(jobContext.getExecutionId());

        if (jobParams.containsKey("datasetId")) {
            datasetId = jobParams.getProperty("datasetId");
            dataset = datasetService.findByGlobalId(datasetId);
        }

        if (jobParams.containsKey("datasetPrimaryKey")) {
            datasetPrimaryKey = Long.parseLong(jobParams.getProperty("datasetPrimaryKey"));
            dataset = datasetService.find(datasetPrimaryKey);
        }

        if (jobParams.containsKey("userPrimaryKey")) {
            userPrimaryKey = Long.parseLong(jobParams.getProperty("userPrimaryKey"));
            user = userServiceBean.find(userPrimaryKey);
        }
    }

    @Override
    public void writeItems(List list) {
        if (permissionServiceBean.userOn(user, dataset.getOwner()).has(Permission.AddDataset)) {
            List<DataFile> datafiles = dataset.getFiles();
            for (Object dataFile : list) {
                datafiles.add((DataFile) dataFile);
                dataset.getLatestVersion().getDataset().setFiles(datafiles);
            }
        } else {
            logger.log(Level.SEVERE, "Unable to save imported datafiles because the authenticted user has " +
                    "insufficient permission.");
        }
    }
}