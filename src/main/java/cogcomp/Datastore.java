package cogcomp;

import edu.illinois.cs.cogcomp.core.io.IOUtils;
import io.minio.MinioClient;
import io.minio.errors.*;
import io.minio.policy.PolicyType;
import org.apache.commons.io.FileUtils;
import org.xmlpull.v1.XmlPullParserException;
import edu.illinois.cs.cogcomp.core.utilities.configuration.ResourceManager;

import java.io.*;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * A thin wrapper over Minio that stores and retrieves immutable data on our machines and supports versioning.
 * Endpoint is an URL, domain name, IPv4 or IPv6 address. Here are examples of valid endpoints:
 *      https://s3.amazonaws.com
 *      https://s3.amazonaws.com/
 *      https://play.minio.io:9000
 *      http://play.minio.io:9010/
 *      localhost
 *      localhost.localdomain
 *      play.minio.io
 *      127.0.0.1
 *      192.168.1.60
 */

public class Datastore {
    private MinioClient minioClient = null;
    private static final String CONFIG_FILE = "datastore-config.properties";

    // this is where we keep the files locally
    private String DATASTORE_FOLDER = System.getProperty("user.home") + File.separator + ".cogcomp-datastore";

    // this is where we keep the temporary files
    private final String TMP_FOLDER = System.getProperty("user.home") + File.separator + ".cogcomp-datastore-tmp";

    public Datastore() throws DatastoreException {
        // Create a minioClient with the information read from configuration file
        ResourceManager rm = null;
        try {
            rm = new ResourceManager(CONFIG_FILE);
        } catch (IOException e) {
            e.printStackTrace();
            throw new DatastoreException("invalid resource keys . . . ");
        }

        String endpoint = rm.getString("ENDPOINT");
        System.out.println(endpoint);
        if(rm.containsKey("ACCESS-KEY") && rm.containsKey("SECRET-KEY")) {
            String accessKey = rm.getString("ACCESS-KEY");
            String secretKey = rm.getString("SECRET-KEY");
            System.out.println("Reading config informtion from file . . . \n");
            System.out.println("\t\tEndpoint: " + endpoint);
            System.out.println("\t\tAccessKey: " + accessKey);
            System.out.println("\t\tSecretKey: " + secretKey);
            try {
                minioClient = new MinioClient(endpoint, accessKey, secretKey);
            } catch (InvalidEndpointException e) {
                e.printStackTrace();
                throw new DatastoreException("Invalid end-point url . . .");
            } catch (InvalidPortException e) {
                e.printStackTrace();
                throw new DatastoreException("Invalid end-point port . . .");
            }
        }
        else {
            try {
                minioClient = new MinioClient(endpoint);
            } catch (InvalidEndpointException e) {
                e.printStackTrace();
                throw new DatastoreException("Invalid end-point url . . .");
            } catch (InvalidPortException e) {
                e.printStackTrace();
                throw new DatastoreException("Invalid end-point port . . .");
            }
        }
        IOUtils.mkdir(DATASTORE_FOLDER);
        IOUtils.mkdir(TMP_FOLDER);
    }

    public Datastore(String endpoint) throws DatastoreException {
        // Creates Minio client object with given endpoint using anonymous access.
        try {
            this.minioClient = new MinioClient(endpoint);
        } catch (InvalidEndpointException e) {
            e.printStackTrace();
            throw new DatastoreException("Invalid end-point url . . .");
        } catch (InvalidPortException e) {
            e.printStackTrace();
            throw new DatastoreException("Invalid end-point port . . .");
        }
        IOUtils.mkdir(DATASTORE_FOLDER);
        IOUtils.mkdir(TMP_FOLDER);
    }

    public Datastore(ResourceManager rm) throws InvalidPortException, InvalidEndpointException {
        String endpoint = rm.getString("datastoreEndpoint");
        this.minioClient = new MinioClient(endpoint);
    }

    public Datastore(String endpoint, String accessKey, String secretKey) throws InvalidPortException, InvalidEndpointException {
        System.out.println("Setting the connection details directly with the constructor . . . ");
        System.out.println("\t\tEndpoint: " + endpoint);
        System.out.println("\t\tAccessKey: " + accessKey);
        System.out.println("\t\tSecretKey: " + secretKey);
        // Create a minioClient with the Minio Server name, Port, Access key and Secret key.
        minioClient = new MinioClient(endpoint, accessKey, secretKey);
        IOUtils.mkdir(DATASTORE_FOLDER);
        IOUtils.mkdir(TMP_FOLDER);
    }

//    public InputStream getFileAsStream(String groupId, String artifactId, Double version) throws IOException, InvalidKeyException, NoSuchAlgorithmException, InsufficientDataException, InvalidArgumentException, InternalException, NoResponseException, InvalidBucketNameException, XmlPullParserException, ErrorResponseException {
//        return minioClient.getObject(groupId, artifactId + Double.toString(version) );
//    }

//    public File getFile(String groupId, String artifactId, Double version) throws IOException, InvalidKeyException, NoSuchAlgorithmException, InsufficientDataException, InvalidArgumentException, InternalException, NoResponseException, InvalidBucketNameException, XmlPullParserException, ErrorResponseException {
//        InputStream fileStream = getFileAsStream(groupId, artifactId, version);
//        byte[] buffer = new byte[fileStream.available()];
//        fileStream.read(buffer);
//        File targetFile = new File(DATASTORE_FOLDER + File.separator +
//                groupId + File.separator + artifactId + Double.toString(version) );
//        OutputStream outStream = new FileOutputStream(targetFile);
//        outStream.write(buffer);
//        return targetFile;
//    }

    public File getFile(String groupId, String artifactId, Double version) throws DatastoreException {
        return getFile(groupId, artifactId, version, false);
    }

    public File getFile(String groupId, String artifactId, Double version, Boolean isPrivate) throws DatastoreException {
        String versionedFileName = getNormalizedArtifactId(artifactId, version);
        String augmentedGroupId = (isPrivate? "private.": "readonly.") + groupId;
        System.out.println("Downloading the file from datastore . . . ");
        System.out.println("\t\tGroupId: " + augmentedGroupId);
        System.out.println("\t\tArtifactId: " + versionedFileName);
        String fileFolder = DATASTORE_FOLDER + File.separator + augmentedGroupId;
        IOUtils.mkdir(fileFolder);
        if(versionedFileName.contains("/")) {
            int idx = versionedFileName.lastIndexOf("/");
            String location = fileFolder + File.separator + versionedFileName.substring(0, idx);
            try {
                FileUtils.forceMkdir(new File(location));
            } catch (IOException e) {
                e.printStackTrace();
                throw new DatastoreException("Unable to create folder in your local machine " + location + " . . .");
            }
        }
        try {
            minioClient.getObject(augmentedGroupId, versionedFileName, fileFolder + File.separator + versionedFileName);
        } catch (InvalidBucketNameException e) {
            e.printStackTrace();
            throw new DatastoreException("Invalid bucket name . . . ");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InsufficientDataException e) {
            e.printStackTrace();
            throw new DatastoreException("Insufficient data . . . ");
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
            throw new DatastoreException("Invalid key . . . ");
        } catch (NoResponseException e) {
            e.printStackTrace();
            throw new DatastoreException("No server response . . . ");
        } catch (XmlPullParserException e) {
            e.printStackTrace();
        } catch (ErrorResponseException e) {
            e.printStackTrace();
        } catch (InternalException e) {
            e.printStackTrace();
        } catch (InvalidArgumentException e) {
            e.printStackTrace();
        }
        return new File(fileFolder + File.separator + versionedFileName);
    }

    /**
     * To publish a file into the datastore, given a groupId, artifactId, and version number. If the file already exist,
     * with the given details, it will give error.
     */
    public void publishFile(String groupId, String artifactId, Double version, String fileName) throws DatastoreException  {
        publishFile(groupId, artifactId, version, fileName, false, false);
    }

    public void publishFile(String groupId, String artifactId, Double version, String fileName, Boolean privateBucket,
                            Boolean overwrite) throws DatastoreException  {
        String augmentedGroupId = (privateBucket? "private.": "readonly.") + groupId;
        try {
            setBucketPolicies(augmentedGroupId, privateBucket);
            String versionedFileName = getNormalizedArtifactId(artifactId, version);

            if(minioClient.listObjects(augmentedGroupId, versionedFileName).iterator().hasNext()) {
                if (!overwrite) {
                    System.out.println("File already exists! Cannot replace it, unless you set the overwrite parameter to be true. ");
                }
                else {
                    System.out.println("File already exists! Overwriting the old file. ");
                }
            }

            System.out.println("Publishing file: ");
            System.out.println("\t\t GroupId: " + augmentedGroupId);
            System.out.println("\t\t ArtifactId " + versionedFileName);
            System.out.println("\t\t FileName: " + fileName);
            minioClient.putObject(augmentedGroupId, versionedFileName, fileName);
        } catch (InvalidBucketNameException e) {
            e.printStackTrace();
            throw new DatastoreException("InvalidBucketName . . . ");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InsufficientDataException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
            throw new DatastoreException("InvalidKeyName . . . ");
        } catch (NoResponseException e) {
            e.printStackTrace();
            throw new DatastoreException("No response from server . . . ");
        } catch (XmlPullParserException e) {
            e.printStackTrace();
        } catch (ErrorResponseException e) {
            e.printStackTrace();
        } catch (InternalException e) {
            e.printStackTrace();
        } catch (InvalidArgumentException e) {
            e.printStackTrace();
        } catch (InvalidObjectPrefixException e) {
            e.printStackTrace();
        }
    }

    private void setBucketPolicies(String augmentedGroupId, boolean privateBucket) throws IOException, InvalidKeyException, NoSuchAlgorithmException, InsufficientDataException, InternalException, NoResponseException, InvalidBucketNameException, XmlPullParserException, ErrorResponseException, InvalidObjectPrefixException {
        // Check if the bucket already exists.
        boolean isExist = minioClient.bucketExists(augmentedGroupId);
        if(isExist) {
            System.out.println("GroupId already exists.");
        } else {
            // Make a new bucket called asiatrip to hold a zip file of photos.
            System.out.println("Making new bucket: " + augmentedGroupId);
            minioClient.makeBucket(augmentedGroupId);
            if(privateBucket) {
                // do nothing: the default behavior is private
                // ref: http://stackoverflow.com/a/42616613/1164246
            }
            else {
                // anonymous users should be able to read the file, if it is not private
                minioClient.setBucketPolicy(augmentedGroupId, "", PolicyType.READ_ONLY);
            }
        }
    }

    public void publishDirectory(String groupId, String artifactId, Double version, String path, Boolean privateBucket,
                            Boolean overwrite) throws DatastoreException  {
        String augmentedGroupId = (privateBucket? "private.": "readonly.") + groupId;
        try {
            setBucketPolicies(augmentedGroupId, privateBucket);
            String versionedFileName = getNormalizedArtifactId(artifactId, version) + ".zip";

            // creating a zip file of the folder
            String zippedFileName = TMP_FOLDER + File.separator + new File(path).getName() + ".zip";
            ZipHelper.zipDir(path, zippedFileName);

            if(minioClient.listObjects(augmentedGroupId, versionedFileName).iterator().hasNext()) {
                if (!overwrite) {
                    System.out.println("Directory already exists! Cannot replace it, unless you set the overwrite parameter to be true. ");
                }
                else {
                    System.out.println("Directory already exists! Overwriting the old file. ");
                }
            }

            File fileObj = new File(zippedFileName);
            String fileSizeReadable = FileUtils.byteCountToDisplaySize(fileObj.length());
            System.out.println("Size of the zipped file: " + fileSizeReadable);
            System.out.println("Publishing directory: ");
            System.out.println("\t\t GroupId: " + augmentedGroupId);
            System.out.println("\t\t ArtifactId " + versionedFileName);
            System.out.println("\t\t FolderPath: " + path);
            System.out.println("\t\t ZippedPath: " + zippedFileName);
            minioClient.putObject(augmentedGroupId, versionedFileName, zippedFileName);
            IOUtils.rm(zippedFileName);
        } catch (InvalidBucketNameException e) {
            e.printStackTrace();
            throw new DatastoreException("InvalidBucketName . . . ");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InsufficientDataException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
            throw new DatastoreException("InvalidKeyName . . . ");
        } catch (NoResponseException e) {
            e.printStackTrace();
            throw new DatastoreException("No response from server . . . ");
        } catch (XmlPullParserException e) {
            e.printStackTrace();
        } catch (ErrorResponseException e) {
            e.printStackTrace();
        } catch (InternalException e) {
            e.printStackTrace();
        } catch (InvalidArgumentException e) {
            e.printStackTrace();
        } catch (InvalidObjectPrefixException e) {
            e.printStackTrace();
        }
    }

    public File getDirectory(String groupId, String artifactId, Double version, Boolean isPrivate) throws DatastoreException {
        String versionedFileName = getNormalizedArtifactId(artifactId, version) + ".zip";
        String augmentedGroupId = (isPrivate? "private.": "readonly.") + groupId;
        System.out.println("Downloading the folder from datastore . . . ");
        System.out.println("\t\tGroupId: " + augmentedGroupId);
        System.out.println("\t\tArtifactId: " + versionedFileName);
        String fileFolder = DATASTORE_FOLDER + File.separator + augmentedGroupId;
        IOUtils.mkdir(fileFolder);
        if(versionedFileName.contains("/")) {
            int idx = versionedFileName.lastIndexOf("/");
            String location = fileFolder + File.separator + versionedFileName.substring(0, idx);
            try {
                FileUtils.forceMkdir(new File(location));
            } catch (IOException e) {
                e.printStackTrace();
                throw new DatastoreException("Unable to create folder in your local machine " + location + " . . .");
            }
        }

        // creating a zip file of the folder
        String zippedFileName = TMP_FOLDER + File.separator + artifactId + ".zip";
        try {
            minioClient.getObject(augmentedGroupId, versionedFileName, zippedFileName);
        } catch (InvalidBucketNameException e) {
            e.printStackTrace();
            throw new DatastoreException("Invalid bucket name . . . ");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InsufficientDataException e) {
            e.printStackTrace();
            throw new DatastoreException("Insufficient data . . . ");
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
            throw new DatastoreException("Invalid key . . . ");
        } catch (NoResponseException e) {
            e.printStackTrace();
            throw new DatastoreException("No server response . . . ");
        } catch (XmlPullParserException e) {
            e.printStackTrace();
        } catch (ErrorResponseException e) {
            e.printStackTrace();
        } catch (InternalException e) {
            e.printStackTrace();
        } catch (InvalidArgumentException e) {
            e.printStackTrace();
        }

        String path = fileFolder + File.separator + version + File.separator + artifactId;
        IOUtils.mkdir(path);

        // unzip the downloaded zip file
        ZipHelper.unZipIt(zippedFileName, path);
        System.out.println("zippedFileName: " + zippedFileName);
        System.out.println("path: " + path);
        System.out.println("artifactId: " + artifactId);
        return new File(path);
    }

    private String getNormalizedArtifactId(String artifactId, Double version) {
        // old way: put the version number between the file name and its extension
        /*
        String extension = FilenameUtils.getExtension(artifactId);
        String fileNameWithoutExtension = artifactId.replace(extension, "");
        fileNameWithoutExtension + "-" + Double.toString(version) + (extension.equals("")? "": "." + extension);
        */
        return version + File.separator + artifactId;
    }

    public static void main(String[] args) {
        Datastore ds = null;
        try {
            ds = new Datastore();
            // publish a public file
            ds.publishFile("edu.cogcomp", "pom", 1.0, "pom.xml");

            // publish a private file
            // ds.publishFile("edu.cogcomp", "pom", 1.0, "pom.xml");

            // publish a file and overwrite the old file
            // ds.publishFile("edu.cogcomp", "pom", 1.0, "pom.xml", true);

            // publish a file and observe overwrite error
            // ds.publishFile("edu.cogcomp", "pom", 1.0, "pom.xml");

            // read a public file
            // File f = ds.getFile("edu.cogcomp", "pom", 1.0);

            // read a public file without credentials
            // Datastore dsNoCredentials = new Datastore("http://smaug.cs.illinois.edu:8080");
            // File f = ds.getFile("edu.cogcomp", "pom", 1.0);

            // read a private file

            // publish a public folder

            // publish a private folder

            // read a public folder

            // read a private folder
        } catch (DatastoreException e) {
            e.printStackTrace();
        }
    }
}
