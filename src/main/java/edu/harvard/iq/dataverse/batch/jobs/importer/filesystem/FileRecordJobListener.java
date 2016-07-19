package edu.harvard.iq.dataverse.batch.jobs.importer.filesystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.harvard.iq.dataverse.Dataset;
import edu.harvard.iq.dataverse.DatasetServiceBean;
import edu.harvard.iq.dataverse.UserNotification;
import edu.harvard.iq.dataverse.UserNotificationServiceBean;
import edu.harvard.iq.dataverse.UserServiceBean;
import edu.harvard.iq.dataverse.actionlogging.ActionLogServiceBean;
import edu.harvard.iq.dataverse.authorization.AuthenticationServiceBean;
import edu.harvard.iq.dataverse.authorization.users.AuthenticatedUser;
import edu.harvard.iq.dataverse.batch.entities.JobExecutionEntity;
import edu.harvard.iq.dataverse.batch.util.LoggingUtil;

import javax.batch.api.BatchProperty;
import javax.batch.api.listener.JobListener;
import javax.batch.api.listener.StepListener;
import javax.batch.operations.JobOperator;
import javax.batch.runtime.BatchRuntime;
import javax.batch.runtime.BatchStatus;
import javax.batch.runtime.JobExecution;
import javax.batch.runtime.context.JobContext;
import javax.batch.runtime.context.StepContext;
import javax.ejb.EJB;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.inject.Named;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

@Named
@Dependent
public class FileRecordJobListener implements StepListener, JobListener {

    private static final Logger logger = Logger.getLogger(FileRecordJobListener.class.getName());

    private static final UserNotification.Type notifyType = UserNotification.Type.FILESYSTEMIMPORT;

    @Inject
    private JobContext jobContext = null;

    @Inject
    private StepContext stepContext;

    @Inject
    @BatchProperty
    private String logDir;

    @EJB
    UserNotificationServiceBean notificationServiceBean;

    @EJB
    AuthenticationServiceBean authenticationServiceBean;

    @EJB
    ActionLogServiceBean actionLogServiceBean;

    @EJB
    DatasetServiceBean datasetServiceBean;

    @EJB
    UserServiceBean userServiceBean;

    @Override
    public void afterStep() throws Exception {
        // no-op
    }

    @Override
    public void beforeStep() throws Exception {
        // no-op
    }

    @Override
    public void beforeJob() throws Exception {
        // no-op
    }

    @Override
    public void afterJob() throws Exception {
        doReport();
        logger.log(Level.INFO, "After Job {0}, instance {1} and execution {2}, batch status [{3}], exit status [{4}]",
                new Object[]{jobContext.getJobName(), jobContext.getInstanceId(), jobContext.getExecutionId(),
                        jobContext.getBatchStatus(), jobContext.getExitStatus()});
    }

    private void doReport() {

        try {

            JobOperator jobOperator = BatchRuntime.getJobOperator();

            String jobJson;
            String userId;
            String datasetId;
            Long datasetVersionId;
            String jobId = Long.toString(jobContext.getInstanceId());

            // determine user and dataset IDs based on job params
            Properties jobParams = jobOperator.getParameters(jobContext.getInstanceId());
            if (jobParams.containsKey("datasetPrimaryKey") && jobParams.containsKey("userPrimaryKey")) {
                datasetId = datasetServiceBean.find(Long.parseLong(jobParams.getProperty("datasetPrimaryKey"))).getGlobalId();
                userId = userServiceBean.find(Long.parseLong(jobParams.getProperty("userPrimaryKey"))).getIdentifier();
            } else if (jobParams.containsKey("datasetId") && jobParams.containsKey("userId")) {
                datasetId = jobParams.getProperty("datasetId");
                userId = jobParams.getProperty("userId");
            } else {
                logger.log(Level.SEVERE, "Unable to report job since there are no job params for user and/or dataset.");
                return;
            }

            AuthenticatedUser user = authenticationServiceBean.getAuthenticatedUser(userId);
            if (user == null) {
                logger.log(Level.SEVERE, "Cannot find authenticated user with ID: " + userId);
                return;
            }

            Dataset dataset = datasetServiceBean.findByGlobalId(datasetId);
            if (dataset == null) {
                logger.log(Level.SEVERE, "Cannot find dataset with ID: " + datasetId);
                return;
            }
            datasetVersionId = dataset.getLatestVersion().getId();

            JobExecution jobExecution = jobOperator.getJobExecution(jobContext.getInstanceId());
            if (jobExecution != null) {

                Date date = new Date();
                Timestamp timestamp =  new Timestamp(date.getTime());

                JobExecutionEntity jobExecutionEntity = JobExecutionEntity.create(jobExecution);
                jobExecutionEntity.setExitStatus("COMPLETED");
                jobExecutionEntity.setStatus(BatchStatus.COMPLETED);
                jobExecutionEntity.setEndTime(date);
                jobJson = new ObjectMapper().writeValueAsString(jobExecutionEntity);

                // [1] save json log to file
                LoggingUtil.saveJsonLog(jobJson, logDir, jobId);
                // [2] send user notifications
                notificationServiceBean.sendNotification(user, timestamp, notifyType, datasetVersionId);
                // [3] action log it
                actionLogServiceBean.log(LoggingUtil.getActionLogRecord(userId, jobExecution, jobJson, jobId));

            } else {
                logger.log(Level.SEVERE, "Job execution is null");
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error creating job json: " + e.getMessage());
        }
    }

}
