package parser.apk;

import com.googlecode.dex2jar.reader.DexFileReader;
import org.apache.commons.io.IOUtils;
import parser.axml.ManifestInfo;
import parser.axml.Parser;
import parser.dex.DexClass;
import parser.dex.DexFileAdapter;
import parser.utils.CertTool;
import parser.utils.FileTypesDetector;
import parser.utils.HashTool;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * apk package information, classes, methods, various....
 * <p/>
 * <p/>
 * ex:
 * <p>
 * APK apk = new APK("test.apk");
 * apk.getCertificateInfos();
 * </p>
 */
public class APK {
    HashMap<String, String> certificateInfos;
    private List<DexClass> dexClasses = new ArrayList<>();
    @SuppressWarnings("UnusedDeclaration")
    private String absolutePath;
    private String dexMd5 = null;
    private String fileName = "";
    private ManifestInfo manifestInfo;
    private DexFileReader dexFileReader;
    private String dexSHA256;
    private ArrayList<String> subFileHash256List;
    private HashMap<String, ElfData> elfDataHashMap;
    private HashMap<String, APK> subApkDataMap;
    private HashMap<String, String> subFileHash256Map;
    private HashMap<String, String> subAPKHash256Map;
    private HashMap<String, String> metaDatas;

    public APK(InputStream inputStream) throws IOException {
        BufferedInputStream bis = new BufferedInputStream(inputStream);
        bis.mark(bis.available());
        certificateInfos = CertTool.getCertificateInfos(bis);
        bis.reset();

        int size;
        byte[] buffer = new byte[2048];
        ZipInputStream zipInputStream = new ZipInputStream(bis);
        ZipEntry entry;
        ByteArrayOutputStream byteArrayOutputStream;
        InputStream amInputStream = null;
        byte[] arscBytes = null;
        byte[] axmlBytes = null;


        while ((entry = zipInputStream.getNextEntry()) != null) {
            if (entry.getName().equals("classes.dex")) {
                byteArrayOutputStream = new ByteArrayOutputStream();
                while ((size = zipInputStream.read(buffer, 0, buffer.length)) != -1) {
                    byteArrayOutputStream.write(buffer, 0, size);
                }
                byte[] bytes = byteArrayOutputStream.toByteArray();
                byteArrayOutputStream.close();

                dexFileReader = new DexFileReader(byteArrayOutputStream.toByteArray());
                dexMd5 = HashTool.getSHA256(bytes);
                continue;
            }

            if (entry.getName().equals("AndroidManifest.xml")) {
                byteArrayOutputStream = new ByteArrayOutputStream();
                while ((size = zipInputStream.read(buffer, 0, buffer.length)) != -1) {
                    byteArrayOutputStream.write(buffer, 0, size);
                }
                axmlBytes = byteArrayOutputStream.toByteArray();
                byteArrayOutputStream.close();
//                amInputStream = new ByteArrayInputStream(bytes);
                continue;
            }

            if (entry.getName().contains(".arsc")) {
                byteArrayOutputStream = new ByteArrayOutputStream();
                while ((size = zipInputStream.read(buffer, 0, buffer.length)) != -1) {
                    byteArrayOutputStream.write(buffer, 0, size);
                }
                arscBytes = byteArrayOutputStream.toByteArray();
                byteArrayOutputStream.close();
            }

        }

        if (axmlBytes != null) {
            System.out.println(axmlBytes.toString());
            Parser parser = new Parser(axmlBytes, arscBytes);
            manifestInfo = parser.getManifestInfo();
        }

//        if (amInputStream != null) {
//            final ManifestParser mp = new ManifestParser();
//            manifestInfo = mp.parse(amInputStream, arscBytes);
//        }

        zipInputStream.close();
        bis.close();
        inputStream.close();
    }


    /**
     * @param filePath  文件路径
     * @param parseAXML 是否解析 AndroidManifest.xml
     * @param parseDex  是否解析 Dex
     * @param parseCert 是否解析证书
     * @throws IOException
     */
    public APK(String filePath, boolean parseAXML, boolean parseDex, boolean parseCert)
            throws IOException {


        File file = new File(filePath);

        absolutePath = file.getAbsolutePath();
        fileName = file.getName();


        if (!FileTypesDetector.isAPK(file)) {
            throw new IOException("IT IS NOT A APK FILE.");
        }


        ZipFile zipFile = new ZipFile(file);
        ZipEntry zipEntry = zipFile.getEntry("classes.dex");
        if (zipEntry != null) {
            dexMd5 = HashTool.getSHA256(IOUtils.toByteArray(zipFile.getInputStream(zipEntry)));
        }
        zipFile.close();

        if (parseAXML) {
            // 解析清单信息

            Parser parser = new Parser(file);
            manifestInfo = parser.getManifestInfo();

            if (manifestInfo == null) {
                throw new FileNotFoundException("No AndroidManifest.xml");
            }
        }

        if (parseDex) {
            initDexFileReader(file);
        }

        if (parseCert) {
            certificateInfos = CertTool.getCertificateInfos(file);
        }

    }

    /**
     * @param file      文件
     * @param parseAXML 是否解析 AndroidManifest.xml
     * @param parseDex  是否解析 Dex
     * @param parseCert 是否解析证书
     * @throws IOException
     */
    public APK(File file, boolean parseAXML, boolean parseDex, boolean parseCert)
            throws IOException {

        absolutePath = file.getAbsolutePath();
        fileName = file.getName();

        if (!FileTypesDetector.isAPK(file)) {
            throw new IOException("IT IS NOT A APK FILE.");
        }

        ZipFile zipFile = new ZipFile(file);
        ZipEntry zipEntry = zipFile.getEntry("classes.dex");
        if (zipEntry != null) {
            dexMd5 = HashTool.getSHA256(IOUtils.toByteArray(zipFile.getInputStream(zipEntry)));
        }
        zipFile.close();

        if (parseAXML) {
            Parser parser = new Parser(file);
            manifestInfo = parser.getManifestInfo();
            if (manifestInfo == null) {
                throw new FileNotFoundException("No AndroidManifest.xml");
            }
        }

        if (parseDex) {
            initDexFileReader(file);
        }

        if (parseCert) {
            certificateInfos = CertTool.getCertificateInfos(file);
        }

    }


    /**
     * 构造函数
     *
     * @param filePath 文件路径
     * @throws IOException
     */
    public APK(String filePath)
            throws Exception {

        File file = new File(filePath);

        absolutePath = file.getAbsolutePath();
        fileName = file.getName();

        if (!FileTypesDetector.isAPK(file)) {
            throw new Exception("IT IS NOT A APK FILE.");
        }


        ZipFile zipFile = new ZipFile(file);
        ZipEntry zipEntry = zipFile.getEntry("classes.dex");
        if (zipEntry != null) {
            dexMd5 = HashTool.getMD5(IOUtils.toByteArray(zipFile.getInputStream(zipEntry)));
            dexSHA256 = HashTool.getSHA256(IOUtils.toByteArray(zipFile.getInputStream(zipEntry)));
        }
        initSubFileList(zipFile);
        zipFile.close();

        initDexFileReader(file);

        // 解析清单信息
        Parser parser = new Parser(file);
        manifestInfo = parser.getManifestInfo();

        if (manifestInfo == null) {
            throw new Exception("No AndroidManifest.xml");
        }


        certificateInfos = CertTool.getCertificateInfos(file);
    }

    /**
     * 构造函数
     *
     * @param file 文件
     * @throws IOException
     */
    public APK(File file) throws IOException {

        if (!FileTypesDetector.isAPK(file)) {
            throw new IOException("IT IS NOT A APK FILE.");
        }

        absolutePath = file.getAbsolutePath();
        fileName = file.getName();

        ZipFile zipFile = new ZipFile(file);
        ZipEntry zipEntry = zipFile.getEntry("classes.dex");

        if (zipEntry != null) {
            dexMd5 = HashTool.getMD5(IOUtils.toByteArray(zipFile.getInputStream(zipEntry)));
            dexSHA256 = HashTool.getSHA256(IOUtils.toByteArray(zipFile.getInputStream(zipEntry)));
        }
        initSubFileList(zipFile);
        zipFile.close();


        Parser parser = new Parser(file);
        manifestInfo = parser.getManifestInfo();

        initDexFileReader(file);
        certificateInfos = CertTool.getCertificateInfos(file);
    }

    public HashMap<String, APK> getSubApkDataMap() {
        return subApkDataMap;
    }

    public HashMap<String, String> getSubAPKHash256Map() {
        return subAPKHash256Map;
    }

    public HashMap<String, ElfData> getElfDataHashMap() {
        return elfDataHashMap;
    }


    private void initSubFileList(ZipFile zipFile) {
        subFileHash256List = new ArrayList<>();
        subFileHash256Map = new HashMap<>();
        subAPKHash256Map = new HashMap<>();
        elfDataHashMap = new HashMap<>();
        subApkDataMap = new HashMap<>();

        ZipEntry zipEntry;
        Enumeration enumeration = zipFile.entries();
        while (enumeration.hasMoreElements()) {
            zipEntry = (ZipEntry) enumeration.nextElement();
            String name = zipEntry.getName();

            try {


                String fileType = FileTypesDetector.getType(zipFile.getInputStream(zipEntry));
                // FIXME elf got some bug.
//                if (fileType.contains("ELF")) {
//                    String hash = HashTool.getSHA256(IOUtils.toByteArray(zipFile.getInputStream(zipEntry)));
//                    Elf elf = new Elf(IOUtils.toByteArray(zipFile.getInputStream(zipEntry)));
//                    List<String> strings = elf.loadStrings();
//
//                    elfDataHashMap.put(name, new ElfData(hash, strings));
//                    continue;
//                }

                String hash = HashTool.getSHA256(IOUtils.toByteArray(zipFile.getInputStream(zipEntry)));
                if (fileType.contains("APK") || fileType.contains("ZIP")
                        && FileTypesDetector.isAPK(zipFile.getInputStream(zipEntry))) {
                    System.out.println(zipEntry.getName());
                    APK apk = new APK(zipFile.getInputStream(zipEntry));

                    subAPKHash256Map.put(name, hash);
                    subApkDataMap.put(name, apk);
                    continue;
                }


                subFileHash256Map.put(name, hash);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void initDexFileReader(File file) throws IOException {
        dexFileReader = new DexFileReader(file);
        dexFileReader.accept(new DexFileAdapter(dexClasses),
                DexFileReader.SKIP_DEBUG | DexFileReader.SKIP_ANNOTATION);
    }

    /**
     * 获取文件名
     *
     * @return 文件名
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * 获取包名
     *
     * @return 包名
     */
    public String getPackageName() {
        return manifestInfo.packageName;
    }

    /**
     * 获取版本号
     *
     * @return 版本号
     */
    public String getVersionCode() {
        return manifestInfo.versionCode;
    }

    /**
     * 获取dexMD5
     *
     * @return dex md5
     */
    @SuppressWarnings("UnusedDeclaration")
    public String getDexMD5() {
        return dexMd5;
    }

    /**
     * 获取dexMD5
     *
     * @return dex md5
     */
    public String getDexSHA256() {
        return dexSHA256;
    }

    /**
     * 获取版本名
     *
     * @return 版本名
     */
    public String getVersionName() {
        return manifestInfo.versionName;
    }

    /**
     * 获取应用名
     *
     * @return 应用名
     */
    public String getLabel() {
        return manifestInfo.label;
    }

    /**
     * 获取权限列表
     *
     * @return ArrayList<String> 权限列表
     */
    public ArrayList<String> getPermissions() {
        return manifestInfo.requestedPermissions;
    }

    /**
     * 获取证书信息
     *
     * @return 格式 MD5：证书信息
     */
    public HashMap<String, String> getCertificateInfos() {
        return certificateInfos;
    }

    /**
     * 获取接收器信息，包含了对应的 INTENT
     *
     * @return 接收器信息
     */
    public HashMap<String, ArrayList<String>> getReceivers() {
        return manifestInfo.receivers;
    }

    /**
     * 获取服务列表
     *
     * @return 服务列表
     */
    public ArrayList<String> getServices() {
        return manifestInfo.services;
    }

    /**
     * 获取 DexFileReader.
     *
     * @return dexFileReader, null 则表示没有dex文件
     */
    public DexFileReader getDexFileReader() {
        return dexFileReader;
    }

    /**
     * 获取 Activity 列表，包含了对应的 INTENT
     *
     * @return Activity 列表
     */
    public HashMap<String, ArrayList<String>> getActivities() {
        return manifestInfo.activities;
    }

    public List<DexClass> getDexClasses() {
        return dexClasses;
    }

    /**
     * 获取所有类/方法/内容
     * La/b/c;->mtd;
     *
     * @return 方法Map <method : method body>
     */
    public HashMap<String, String> getMethods() {
        HashMap<String, String> methods = new HashMap<>();

        for (DexClass dexClass : dexClasses) {
            if (dexClass.methodMap.size() > 0) {
                for (String key : dexClass.methodMap.keySet()) {
                    methods.put(key, dexClass.methodMap.get(key));
                }
            }
        }

        return methods;
    }

    /**
     * 获取子包?
     */
    @SuppressWarnings("UnusedDeclaration")
    public void getSubApks() {

    }

    /**
     * @return @return <class : set(String)>
     */
    public HashMap<String, HashSet<String>> getStringsMap() {
        HashMap<String, HashSet<String>> stringMap = new HashMap<>();

        final List<DexClass> dexClasses = new ArrayList<>();

        try {
            for (DexClass dexClass : dexClasses) {
                stringMap.put(dexClass.className, new HashSet<>(dexClass.stringData));
            }
        } catch (java.lang.OutOfMemoryError error) {
            error.printStackTrace();
        }

        return stringMap;
    }

    /**
     * 获得 DEX 中存在的字符
     *
     * @return 获取字符串
     */
    public List<String> getStrings() {
        return dexFileReader.loadStrings();
    }

    @Override
    public String toString() {
        return "APK{" +
                "certificateInfos=" + certificateInfos +
                ", dexMd5='" + dexMd5 + '\'' +
                ", fileName='" + fileName + '\'' +
                ", manifestInfo=" + manifestInfo +
                '}';
    }


    public HashMap<String, String> getSubFileHash256Map() {
        return subFileHash256Map;
    }

    @SuppressWarnings("UnusedDeclaration")
    public ArrayList<String> getSubFileHash256List() {
        return subFileHash256List;
    }


    public HashMap<String, String> getMetaDatas() {
        if (metaDatas == null) {
            metaDatas = manifestInfo.metaData;
        }
        return metaDatas;
    }

    public class ElfData {
        String hash;
        List<String> stringList;

        private ElfData(String hash, List<String> stringList) {
            this.hash = hash;
            this.stringList = stringList;
        }

        public String getHash() {
            return hash;
        }

        public List<String> getStringList() {
            return stringList;
        }


    }
}
