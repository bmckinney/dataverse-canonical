package edu.harvard.iq.dataverse.api.filesystem;

/*
   Copyright (C) 2005-2016, by the President and Fellows of Harvard College.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

   Dataverse Network - A web application to share, preserve and analyze research data.
   Developed at the Institute for Quantitative Social Science, Harvard University.
*/

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.path.json.JsonPath;
import com.jayway.restassured.response.Response;
import edu.harvard.iq.dataverse.batch.entities.JobExecutionEntity;
import edu.harvard.iq.dataverse.batch.entities.StepExecutionEntity;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.batch.runtime.BatchStatus;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.UUID;

import static com.jayway.restassured.RestAssured.given;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;
import static org.hamcrest.Matchers.equalTo;

/**
 * Batch File System Import Job Integration Tests
 *
 * Gothchas:
 *
 * - if you are testing from a dev server rebuilt from scratch, edit permissions such that:
 *   "Anyone with a Dataverse account can add sub dataverses and datasets"
 *
 */
public class FileRecordJobIT {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static ClassLoader classLoader = FileRecordJobIT.class.getClassLoader();
    private static final String SEP = File.separator;

    // test properties
    private static String testName;
    private static String token;

    // dataset properties
    private static String dsGlobalId;
    private static String dsDoi;
    private static String dsDir;
    private static int dsId;
    private static JsonPath dsPath;

    private static Properties props = new Properties();

    @BeforeClass
    public static void setUpClass() throws Exception {

        InputStream input = null;

        try {
            input = classLoader.getResourceAsStream("FileRecordJobIT.properties");
            props.load(input);
            RestAssured.baseURI = props.getProperty("baseuri");
            String port = props.getProperty("port");
            RestAssured.port = Integer.valueOf(port);
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Before
    public void setUpDataverse() {

        try {
            String dsIdentifier;
            // create random test name
            testName = UUID.randomUUID().toString().substring(0, 8);
            // create user and set token
            token = given()
                    .body("{" +
                            "   \"userName\": \"" + testName + "\"," +
                            "   \"firstName\": \"" + testName + "\"," +
                            "   \"lastName\": \"" + testName + "\"," +
                            "   \"email\": \"" + testName + "@mailinator.com\"" +
                            "}")
                    .contentType(ContentType.JSON)
                    .request()
                    .post("/api/builtin-users/secret/" + props.getProperty("builtin.user.key"))
                    .then().assertThat().statusCode(200)
                    .extract().jsonPath().getString("data.apiToken");
            System.out.println("TOKEN: " + token);
            // create dataverse
            given().body("{" +
                    "    \"name\": \"" + testName + "\"," +
                    "    \"alias\": \"" + testName + "\"," +
                    "    \"affiliation\": \"Test-Driven University\"," +
                    "    \"dataverseContacts\": [" +
                    "        {" +
                    "            \"contactEmail\": \"test@example.com\"" +
                    "        }," +
                    "        {" +
                    "            \"contactEmail\": \"test@example.org\"" +
                    "        }" +
                    "    ]," +
                    "    \"permissionRoot\": true," +
                    "    \"description\": \"test Description.\"" +
                    "}")
                    .contentType(ContentType.JSON).request()
                    .post("/api/dataverses/:root?key=" + token)
                    .then().assertThat().statusCode(201);
            System.out.println("DATAVERSE: http://localhost:8080/dataverse/" + testName);
            // create dataset and set id
            String json = IOUtils.toString(classLoader.getResourceAsStream("json/dataset-finch1.json"));
            dsId = given()
                    .header(props.getProperty("api.token.http.header"), token)
                    .body(IOUtils.toString(classLoader.getResourceAsStream("json/dataset-finch1.json")))
                    .contentType("application/json")
                    .post("/api/dataverses/" + testName + "/datasets")
                    .then().assertThat().statusCode(201)
                    .extract().jsonPath().getInt("data.id");
            // get dataset identifier
            dsIdentifier = given()
                    .header(props.getProperty("api.token.http.header"), token)
                    .get("/api/datasets/" + dsId)
                    .then().assertThat().statusCode(200)
                    .extract().jsonPath().getString("data.identifier");
            dsGlobalId = "doi:" + props.getProperty("authority") + SEP + dsIdentifier;
            System.out.println("IDENTIFIER: " + dsIdentifier);
            dsDoi = props.getProperty("authority") + SEP + dsIdentifier;
            dsDir = props.getProperty("data.dir") + dsIdentifier + SEP;
            System.out.println("DATA DIR: " + dsDir);
        } catch (IOException ioe) {
            System.out.println("Error creating test dataset: " + ioe.getMessage());
            fail();
        }
    }

    @AfterClass
    public static void tearDownClass() {
        RestAssured.reset();
    }

    @After
    public void tearDownDataverse() {
        try {
            // delete dataset
            given().header(props.getProperty("api.token.http.header"), token)
                    .delete("/api/datasets/" + dsId)
                    .then().assertThat().statusCode(200);
            // delete dataverse
            given().header(props.getProperty("api.token.http.header"), token)
                    .delete("/api/dataverses/" + testName)
                    .then().assertThat().statusCode(200);
            // delete user
            given().header(props.getProperty("api.token.http.header"), token)
                    .delete("/api/admin/authenticatedUsers/"+testName+"/")
                    .then().assertThat().statusCode(200);
            FileUtils.deleteDirectory(new File(dsDir));
        } catch (IOException ioe) {
            System.out.println("Error creating test dataset: " + ioe.getMessage());
            ioe.printStackTrace();
            fail();
        }
    }

    /**
     * Import the same file in different directories, in the same dataset.
     * This is not permitted via HTTP file upload since identical checksums are not allowed in the same dataset.
     * Ignores failed checksum manifest import.
     */
    @Test
    public void testSameFileInDifferentDirectories() {

        try {

            // create a single test file and put it in two places
            String file1 =  "testfile.txt";
            String file2 = "subdir/testfile.txt";
            File file = createTestFile(dsDir, file1, 0.25);
            if (file != null) {
                FileUtils.copyFile(file, new File(dsDir + file2));
            } else {
                System.out.println("Unable to copy file: " + dsDir + file2);
                fail();
            }

            // mock the checksum manifest
            String checksum1 = "asfdasdfasdfasdf";
            String checksum2 = "sgsdgdsgfsdgsdgf";
            if (file1 != null && file2 != null) {
                PrintWriter pw = new PrintWriter(new FileWriter(dsDir + "/files.sha"));
                pw.write(checksum1 + " " + file1);
                pw.write("\n");
                pw.write(checksum2 + " " + file2);
                pw.write("\n");
                pw.close();
            } else {
                fail();
            }

            // validate job
            JobExecutionEntity job = getJob();
            assertEquals(job.getSteps().size(), 2);
            StepExecutionEntity step1 = job.getSteps().get(0);
            Map<String, Long> metrics = step1.getMetrics();
            assertEquals(job.getExitStatus(), BatchStatus.COMPLETED.name());
            assertEquals(job.getStatus(), BatchStatus.COMPLETED);
            assertEquals(step1.getExitStatus(), BatchStatus.COMPLETED.name());
            assertEquals(step1.getStatus(), BatchStatus.COMPLETED);
            assertEquals(step1.getName(), "import-files");
            assertEquals((long) metrics.get("write_skip_count"), 0);
            assertEquals((long) metrics.get("commit_count"), 1);
            assertEquals((long) metrics.get("process_skip_count"), 0);
            assertEquals((long) metrics.get("read_skip_count"), 0);
            assertEquals((long) metrics.get("write_count"), 2);
            assertEquals((long) metrics.get("rollback_count"), 0);
            assertEquals((long) metrics.get("filter_count"), 0);
            assertEquals((long) metrics.get("read_count"), 2);
            assertEquals(step1.getPersistentUserData(), null);

            // confirm data files were imported
            updateDatasetJsonPath();
            List<String> storageIds = new ArrayList<>();
            storageIds.add(dsPath.getString("data.latestVersion.files[0].dataFile.storageIdentifier"));
            storageIds.add(dsPath.getString("data.latestVersion.files[1].dataFile.storageIdentifier"));
            assert(storageIds.contains(file1));
            assert(storageIds.contains(file2));

            // test the reporting apis
            given()
                    .header(props.getProperty("api.token.http.header"), token)
                    .get(props.getProperty("job.status.api") + job.getId())
                    .then().assertThat()
                    .body("status", equalTo("COMPLETED"));
            List<Integer> ids =  given()
                    .header(props.getProperty("api.token.http.header"), token)
                    .get("/api/batch/jobs/")
                    .then().extract().jsonPath()
                    .getList("jobs.id");
            assertTrue(ids.contains((int)job.getId()));

        } catch (Exception e) {
            System.out.println("Error testIdenticalFilesInDifferentDirectories: " + e.getMessage());
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void testFilesWithChecksumManifest() {

        try {
            // create test files and checksum manifest
            File file1 = createTestFile(dsDir, "testfile1.txt", 0.25);
            File file2 = createTestFile(dsDir, "testfile2.txt", 0.25);
            String checksum1 = "asfdasdfasdfasdf";
            String checksum2 = "sgsdgdsgfsdgsdgf";
            if (file1 != null && file2 != null) {
                PrintWriter pw = new PrintWriter(new FileWriter(dsDir + "/files.sha"));
                pw.write(checksum1 + " " + file1.getName());
                pw.write("\n");
                pw.write(checksum2 + " " + file2.getName());
                pw.write("\n");
                pw.close();
            } else {
                fail();
            }

            JobExecutionEntity job = getJob();
            assertEquals(job.getSteps().size(), 2);
            StepExecutionEntity step1 = job.getSteps().get(0);
            StepExecutionEntity step2 = job.getSteps().get(1);
            Map<String, Long> metrics1 = step1.getMetrics();
            Map<String, Long> metrics2 = step2.getMetrics();
            // check job status
            assertEquals(BatchStatus.COMPLETED.name(), job.getExitStatus());
            assertEquals(BatchStatus.COMPLETED, job.getStatus());
            // check step 1 status and name
            assertEquals(step1.getExitStatus(), BatchStatus.COMPLETED.name());
            assertEquals(step1.getStatus(), BatchStatus.COMPLETED);
            assertEquals(step1.getName(), "import-files");
            // verify step 1 metrics
            assertEquals((long) metrics1.get("write_skip_count"), 0);
            assertEquals((long) metrics1.get("commit_count"), 1);
            assertEquals((long) metrics1.get("process_skip_count"), 0);
            assertEquals((long) metrics1.get("read_skip_count"), 0);
            assertEquals((long) metrics1.get("write_count"), 2);
            assertEquals((long) metrics1.get("rollback_count"), 0);
            assertEquals((long) metrics1.get("filter_count"), 0);
            assertEquals((long) metrics1.get("read_count"), 2);
            // should be no user data (error messages)
            assertEquals(step1.getPersistentUserData(), null);
            // check step 2 status and name
            assertEquals(step2.getExitStatus(), BatchStatus.COMPLETED.name());
            assertEquals(step2.getStatus(), BatchStatus.COMPLETED);
            assertEquals(step2.getName(), "import-checksums");
            // verify step 2 metrics
            assertEquals((long) metrics2.get("write_skip_count"), 0);
            assertEquals((long) metrics2.get("commit_count"), 1);
            assertEquals((long) metrics2.get("process_skip_count"), 0);
            assertEquals((long) metrics2.get("read_skip_count"), 0);
            assertEquals((long) metrics2.get("write_count"), 2);
            assertEquals((long) metrics2.get("rollback_count"), 0);
            assertEquals((long) metrics2.get("filter_count"), 0);
            assertEquals((long) metrics2.get("read_count"), 2);
            // should be no user data (error messages)
            assertEquals(step2.getPersistentUserData(), null);

            // confirm files were imported
            updateDatasetJsonPath();
            List<String> filenames = new ArrayList<>();
            filenames.add(dsPath.getString("data.latestVersion.files[0].dataFile.filename"));
            filenames.add(dsPath.getString("data.latestVersion.files[1].dataFile.filename"));
            assert(filenames.contains("testfile1.txt"));
            assert(filenames.contains("testfile2.txt"));

            // confirm checksums were imported
            List<String> checksums = new ArrayList<>();
            checksums.add(dsPath.getString("data.latestVersion.files[0].dataFile.checksum.value"));
            checksums.add(dsPath.getString("data.latestVersion.files[1].dataFile.checksum.value"));
            assert(checksums.contains(checksum1));
            assert(checksums.contains(checksum2));

        } catch (Exception e) {
            System.out.println("Error testChecksumImport: " + e.getMessage());
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void testFilesWithoutChecksumManifest() {

        try {

            // create test files and NO checksum manifest
            createTestFile(dsDir, "testfile1.txt", 0.25);
            createTestFile(dsDir, "testfile2.txt", 0.25);

            JobExecutionEntity job = getJob();
            assertEquals(job.getSteps().size(), 2);
            StepExecutionEntity step1 = job.getSteps().get(0);
            StepExecutionEntity step2 = job.getSteps().get(1);
            Map<String, Long> metrics1 = step1.getMetrics();
            Map<String, Long> metrics2 = step2.getMetrics();
            // check job status
            assertEquals(job.getExitStatus(), BatchStatus.COMPLETED.name());
            assertEquals(job.getStatus(), BatchStatus.COMPLETED);
            // check step 1 status and name
            assertEquals(step1.getExitStatus(), BatchStatus.COMPLETED.name());
            assertEquals(step1.getStatus(), BatchStatus.COMPLETED);
            assertEquals(step1.getName(), "import-files");
            // verify step 1 metrics
            assertEquals((long) metrics1.get("write_skip_count"), 0);
            assertEquals((long) metrics1.get("commit_count"), 1);
            assertEquals((long) metrics1.get("process_skip_count"), 0);
            assertEquals((long) metrics1.get("read_skip_count"), 0);
            assertEquals((long) metrics1.get("write_count"), 2);
            assertEquals((long) metrics1.get("rollback_count"), 0);
            assertEquals((long) metrics1.get("filter_count"), 0);
            assertEquals((long) metrics1.get("read_count"), 2);
            // should be no user data (error messages)
            assertEquals(step1.getPersistentUserData(), null);
            // check step 2 status and name
            assertEquals(step2.getExitStatus(), BatchStatus.FAILED.name());
            assertEquals(step2.getStatus(), BatchStatus.COMPLETED);
            assertEquals(step2.getName(), "import-checksums");
            // verify step 2 metrics
            assertEquals((long) metrics2.get("write_skip_count"), 0);
            assertEquals((long) metrics2.get("commit_count"), 1);
            assertEquals((long) metrics2.get("process_skip_count"), 0);
            assertEquals((long) metrics2.get("read_skip_count"), 0);
            assertEquals((long) metrics2.get("write_count"), 0);
            assertEquals((long) metrics2.get("rollback_count"), 0);
            assertEquals((long) metrics2.get("filter_count"), 0);
            assertEquals((long) metrics2.get("read_count"), 0);
            // should include detailed error message
            assert(step2.getPersistentUserData().contains("FAILED: missing checksums"));

            // confirm files were imported and checksums unknown
            updateDatasetJsonPath();
            List<String> filenames = new ArrayList<>();
            filenames.add(dsPath.getString("data.latestVersion.files[0].dataFile.filename"));
            filenames.add(dsPath.getString("data.latestVersion.files[1].dataFile.filename"));
            assert(filenames.contains("testfile1.txt"));
            assert(filenames.contains("testfile2.txt"));
            assert(dsPath.getString("data.latestVersion.files[0].dataFile.checksum.value").equalsIgnoreCase("unknown"));
            assert(dsPath.getString("data.latestVersion.files[1].dataFile.checksum.value").equalsIgnoreCase("unknown"));

        } catch (Exception e) {
            System.out.println("Error testChecksumImportMissingManifest: " + e.getMessage());
            e.printStackTrace();
            fail();
        }

    }

    @Test
    public void testFileMissingInChecksumManifest() {

        try {

            // create test files and checksum manifest with just one of the files
            File file1 = createTestFile(dsDir, "testfile1.txt", 0.25);
            File file2 = createTestFile(dsDir, "testfile2.txt", 0.25);
            String checksum1 = "";
            if (file1 != null && file2 != null) {
                PrintWriter pw = new PrintWriter(new FileWriter(dsDir + "/files.sha"));
                checksum1 = "asasdlfkj880asfdasflj";
                pw.write(checksum1 + " " + file1.getName());
                pw.write("\n");
                pw.close();
            } else {
                fail();
            }

            JobExecutionEntity job = getJob();
            assertEquals(job.getSteps().size(), 2);
            StepExecutionEntity step1 = job.getSteps().get(0);
            StepExecutionEntity step2 = job.getSteps().get(1);
            Map<String, Long> metrics1 = step1.getMetrics();
            Map<String, Long> metrics2 = step2.getMetrics();
            // check job status
            assertEquals(job.getExitStatus(), BatchStatus.COMPLETED.name());
            assertEquals(job.getStatus(), BatchStatus.COMPLETED);
            // check step 1 status and name
            assertEquals(step1.getExitStatus(), BatchStatus.COMPLETED.name());
            assertEquals(step1.getStatus(), BatchStatus.COMPLETED);
            assertEquals(step1.getName(), "import-files");
            // verify step 1 metrics
            assertEquals((long) metrics1.get("write_skip_count"), 0);
            assertEquals((long) metrics1.get("commit_count"), 1);
            assertEquals((long) metrics1.get("process_skip_count"), 0);
            assertEquals((long) metrics1.get("read_skip_count"), 0);
            assertEquals((long) metrics1.get("write_count"), 2);
            assertEquals((long) metrics1.get("rollback_count"), 0);
            assertEquals((long) metrics1.get("filter_count"), 0);
            assertEquals((long) metrics1.get("read_count"), 2);
            // should be no user data (error messages)
            assertEquals(step1.getPersistentUserData(), null);
            // check step 2 status and name
            assertEquals(step2.getExitStatus(), BatchStatus.FAILED.name());
            assertEquals(step2.getStatus(), BatchStatus.COMPLETED);
            assertEquals(step2.getName(), "import-checksums");
            // verify step 2 metrics
            assertEquals((long) metrics2.get("write_skip_count"), 0);
            assertEquals((long) metrics2.get("commit_count"), 1);
            assertEquals((long) metrics2.get("process_skip_count"), 0);
            assertEquals((long) metrics2.get("read_skip_count"), 0);
            assertEquals((long) metrics2.get("write_count"), 1);
            assertEquals((long) metrics2.get("rollback_count"), 0);
            assertEquals((long) metrics2.get("filter_count"), 0);
            assertEquals((long) metrics2.get("read_count"), 1);
            // should include detailed error message
            assert(step2.getPersistentUserData().contains("FAILED: missing checksums [testfile2.txt]"));

            // confirm files were imported
            updateDatasetJsonPath();
            List<String> filenames = new ArrayList<>();
            filenames.add(dsPath.getString("data.latestVersion.files[0].dataFile.filename"));
            filenames.add(dsPath.getString("data.latestVersion.files[1].dataFile.filename"));
            assert(filenames.contains("testfile1.txt"));
            assert(filenames.contains("testfile2.txt"));
            // confirm one checksums was imported, one not
            List<String> checksums = new ArrayList<>();
            checksums.add(dsPath.getString("data.latestVersion.files[0].dataFile.checksum.value"));
            checksums.add(dsPath.getString("data.latestVersion.files[1].dataFile.checksum.value"));
            assert(checksums.contains(checksum1));
            assert(checksums.contains("Unknown"));

        } catch (Exception e) {
            System.out.println("Error testChecksumImport: " + e.getMessage());
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void testFileInChecksumManifestDoesntExist() {

        try {

            // create test files and checksum manifest with record that doesn't exist
            File file1 = createTestFile(dsDir, "testfile1.txt", 0.25);
            File file2 = createTestFile(dsDir, "testfile2.txt", 0.25);
            String checksum1 = "aorjsonaortargj848";
            String checksum2 = "ldgklrrshfdsnosri4948";
            if (file1 != null && file2 != null) {
                PrintWriter pw = new PrintWriter(new FileWriter(dsDir + "/files.sha"));
                pw.write(checksum1 + " " + file1.getName());
                pw.write("\n");
                pw.write(checksum2 + " " + file2.getName());
                pw.write("\n");
                pw.write("asdfae34034asfaf9r3  fileThatDoesntExist.txt");
                pw.write("\n");
                pw.close();
            } else {
                fail();
            }

            JobExecutionEntity job = getJob();
            assertEquals(job.getSteps().size(), 2);
            StepExecutionEntity step1 = job.getSteps().get(0);
            StepExecutionEntity step2 = job.getSteps().get(1);
            Map<String, Long> metrics1 = step1.getMetrics();
            Map<String, Long> metrics2 = step2.getMetrics();
            // check job status
            assertEquals(job.getExitStatus(), BatchStatus.COMPLETED.name());
            assertEquals(job.getStatus(), BatchStatus.COMPLETED);
            // check step 1 status and name
            assertEquals(step1.getExitStatus(), BatchStatus.COMPLETED.name());
            assertEquals(step1.getStatus(), BatchStatus.COMPLETED);
            assertEquals(step1.getName(), "import-files");
            // verify step 1 metrics
            assertEquals((long) metrics1.get("write_skip_count"), 0);
            assertEquals((long) metrics1.get("commit_count"), 1);
            assertEquals((long) metrics1.get("process_skip_count"), 0);
            assertEquals((long) metrics1.get("read_skip_count"), 0);
            assertEquals((long) metrics1.get("write_count"), 2);
            assertEquals((long) metrics1.get("rollback_count"), 0);
            assertEquals((long) metrics1.get("filter_count"), 0);
            assertEquals((long) metrics1.get("read_count"), 2);
            // should be no user data (error messages)
            assertEquals(step1.getPersistentUserData(), null);
            // check step 2 status and name
            assertEquals(step2.getExitStatus(), BatchStatus.FAILED.name());
            assertEquals(step2.getStatus(), BatchStatus.COMPLETED);
            assertEquals(step2.getName(), "import-checksums");
            // verify step 2 metrics
            assertEquals((long) metrics2.get("write_skip_count"), 0);
            assertEquals((long) metrics2.get("commit_count"), 1);
            assertEquals((long) metrics2.get("process_skip_count"), 0);
            assertEquals((long) metrics2.get("read_skip_count"), 0);
            assertEquals((long) metrics2.get("write_count"), 2);
            assertEquals((long) metrics2.get("rollback_count"), 0);
            assertEquals((long) metrics2.get("filter_count"), 1);
            assertEquals((long) metrics2.get("read_count"), 3);
            // should report missing data file
            assert(step2.getPersistentUserData().contains("FAILED: missing data files [fileThatDoesntExist.txt]"));

            // confirm files were imported
            updateDatasetJsonPath();
            List<String> filenames = new ArrayList<>();
            filenames.add(dsPath.getString("data.latestVersion.files[0].dataFile.filename"));
            filenames.add(dsPath.getString("data.latestVersion.files[1].dataFile.filename"));
            assert(filenames.contains("testfile1.txt"));
            assert(filenames.contains("testfile2.txt"));
            // confirm checksums were imported
            List<String> checksums = new ArrayList<>();
            checksums.add(dsPath.getString("data.latestVersion.files[0].dataFile.checksum.value"));
            checksums.add(dsPath.getString("data.latestVersion.files[1].dataFile.checksum.value"));
            assert(checksums.contains(checksum1));
            assert(checksums.contains(checksum2));

        } catch (Exception e) {
            System.out.println("Error testChecksumImport: " + e.getMessage());
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void testNoDatasetFound() {
        try {
            String fakeDoi = "10.0001/FK2/FAKE";
            // run batch job
            String dsNotFound  = given()
                    .header(props.getProperty("api.token.http.header"), token)
                    .get(props.getProperty("filesystem.api") + "/" + fakeDoi)
                    .then().assertThat().statusCode(400)
                    .extract().jsonPath().getString("message");
            assertEquals("Can't find dataset with ID: doi:" + fakeDoi, dsNotFound);

        } catch (Exception e) {
            System.out.println("Error testNoDatasetFound: " + e.getMessage());
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void testUnauthorizedUser() {
        try {
            // create unauthorized user
            String unauthUser = UUID.randomUUID().toString().substring(0, 8);
            String unauthToken = given()
                    .body("{" +
                            "   \"userName\": \"" + unauthUser + "\"," +
                            "   \"firstName\": \"" + unauthUser + "\"," +
                            "   \"lastName\": \"" + unauthUser + "\"," +
                            "   \"email\": \"" + unauthUser + "@mailinator.com\"" +
                            "}")
                    .contentType(ContentType.JSON)
                    .request()
                    .post("/api/builtin-users/secret/" + props.getProperty("builtin.user.key"))
                    .then().assertThat().statusCode(200)
                    .extract().jsonPath().getString("data.apiToken");

            // attempt to run batch job as unauthorized user
            String message  = given()
                    .header(props.getProperty("api.token.http.header"), unauthToken)
                    .get(props.getProperty("filesystem.api") + "/" + dsDoi)
                    .then().assertThat().statusCode(403)
                    .extract().jsonPath().getString("message");
            assertEquals("User is not authorized.", message);

            // delete unauthorized user
            given().header(props.getProperty("api.token.http.header"), token)
                    .delete("/api/admin/authenticatedUsers/"+unauthUser+"/")
                    .then().assertThat().statusCode(200);

        } catch (Exception e) {
            System.out.println("Error testUnauthorizedUser: " + e.getMessage());
            e.printStackTrace();
            fail();
        }
    }

    // UTILS

    /***
     * Create a test file with a size in GB
     *
     * @param dir where to save the file (directory will be created if it doesn't exist)
     * @param name the test file name
     * @param size the desired size in GB
     * @return the file
     */
    static File createTestFile(String dir, String name, double size) {

        try {
            File myDir = new File(dir);
            Random random = new Random();
            boolean isDirCreated = myDir.exists();
            if (!isDirCreated) {
                isDirCreated = myDir.mkdirs();
            }
            if (isDirCreated) {
                File file = new File(dir + SEP + name);
                long start = System.currentTimeMillis();
                PrintWriter writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(
                        new FileOutputStream(file), "UTF-8")), false);
                int counter = 0;
                while (true) {
                    String sep = "";
                    for (int i = 0; i < 100; i++) {
                        int number = random.nextInt(1000) + 1;
                        writer.print(sep);
                        writer.print(number / 1e3);
                        sep = " ";
                    }
                    writer.println();
                    if (++counter == 20000) {
                        //System.out.printf("Size: %.3f GB%n", file.length() / 1e9);
                        if (file.length() >= size * 1e9) {
                            writer.close();
                            break;
                        } else {
                            counter = 0;
                        }
                    }
                }
                long time = System.currentTimeMillis() - start;
                //System.out.printf("Took %.1f seconds to create a file of %.3f GB", time / 1e3, file.length() / 1e9);
                return file;
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
            return null;
        }
        return null;
    }

    /**
     * Get the SHA1 checksum for a file.
     *
     * @param file absolute path to file
     * @param format the checksum format (e.g., SHA1, MD5)
     * @return the checksum for the file as a hex string
     */
    static String getFileChecksum(String file, String format) {
        try {
            MessageDigest md = MessageDigest.getInstance(format);
            FileInputStream fis = new FileInputStream(file);
            byte[] dataBytes = new byte[1024];

            int nread;
            while ((nread = fis.read(dataBytes)) != -1) {
                md.update(dataBytes, 0, nread);
            }

            byte[] mdbytes = md.digest();

            //convert the byte to hex format
            StringBuilder sb = new StringBuilder("");
            for (byte b : mdbytes) {
                sb.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
            }

            return sb.toString();

        } catch (Exception e) {
            System.out.println("Error getting " + format + " checksum for " + file + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public static String pollJobStatus(String jobId, String apiToken, int retry, long sleep) {
        int maxTries = 0;
        String json = "";
        String status = BatchStatus.STARTED.name();
        try {
            while (!status.equalsIgnoreCase(BatchStatus.COMPLETED.name())) {
                if (maxTries < retry) {
                    maxTries++;
                    Thread.sleep(sleep);
                    Response jobResponse = given()
                            .header(props.getProperty("api.token.http.header"), apiToken)
                            .get(props.getProperty("job.status.api") + jobId);
                    json = jobResponse.body().asString();
                    status = JsonPath.from(json).getString("status");
                    System.out.println("JOB STATUS: " + status);
                } else {
                    break;
                }
            }
        }catch (InterruptedException ie) {
            System.out.println(ie.getMessage());
            ie.printStackTrace();
        }
        return json;
    }

    private JobExecutionEntity getJob() {
        System.out.println("JOB API: " + props.getProperty("filesystem.api") + "/" + dsDoi);
        try {
            // run batch job and wait for result
            String jobId = given()
                    .header(props.getProperty("api.token.http.header"), token)
                    .get(props.getProperty("filesystem.api") + "/" + dsDoi)
                    .then().assertThat().statusCode(200)
                    .extract().jsonPath().getString("data.executionId");
            String jobResult = pollJobStatus(jobId, token, Integer.valueOf(props.getProperty("polling.retries")),
                    Integer.valueOf(props.getProperty("polling.wait")));
            System.out.println("JOB JSON: " + jobResult);
            return mapper.readValue(jobResult, JobExecutionEntity.class);
        } catch (IOException ioe) {
            System.out.println("Error getting job execution entity: " + ioe.getMessage());
            return null;
        }
    }

    private void updateDatasetJsonPath() {
        System.out.println("API: " + props.getProperty("dataset.api") + dsGlobalId);
        dsPath = given()
                .header(props.getProperty("api.token.http.header"), token)
                .contentType(ContentType.JSON)
                .get(props.getProperty("dataset.api") + dsGlobalId)
                .then().assertThat().statusCode(200)
                .extract().jsonPath();
    }

}
