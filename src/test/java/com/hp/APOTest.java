package com.hp;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.hp.utils.APOUtil;
import com.hp.utils.FileUtil;
import org.crypto.sse.*;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/** APOSSE测试类 */
public class APOTest {
    String inPath = "text/input/";
    String shaPath = "text/shard/";
    String outPath = "text/output/";

    @Test
    public void getObfuscateKeywordsTest() throws Exception {
        Multimap<String,String> keywords = HashMultimap.create();
        for(int i = 0;i < 10;i++){
            keywords.put("key1","ind" + i);
        }
        /*
        File[] files = FileUtil.getFiles(inPath);
        ArrayList<File> files = new ArrayList<File>();
        TextProc.listf(inPath,files);
        TextProc.TextProc(false,inPath);
        */
        File[] files = FileUtil.getFiles(inPath);
        ArrayList<File> fileList = new ArrayList<File>();
        for(File file : files){
            fileList.add(file);
        }
        Multimap<String,String> obfuscateKeywords = APOUtil.getObfuscateKeywords(keywords,fileList);
        System.out.println(obfuscateKeywords.toString());
    }
    @Test
    public void encodeTest() throws Exception{
        //FileUtil.deleteFiles(shaPath);

        File file = new File(inPath);
        File[] files = file.listFiles();
        APOUtil.erasureEncode(files,inPath,shaPath);
    }
    @Test
    public void decodeTest() throws Exception{
        List<String> filesStr = null;
        File[] files = FileUtil.getFiles(inPath);
        for(File file : files){
            filesStr.add(file.getAbsolutePath());
        }
        List<String> decodeFiles = APOUtil.erasureDecode(filesStr,shaPath,outPath);
        System.out.println(decodeFiles.toString());
    }
    @Test
    public void searchTest() throws Exception{
        int bigBlock = 1000;
        int smallBlock = 100;
        int dataSize = 10000;

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
        //Scanner scanner = new Scanner(System.in);
        FileUtil.deleteFiles(shaPath);
        FileUtil.deleteFiles(outPath);
        //File[] files = FileUtil.getFiles(inPath);

        /*
        System.out.println("Enter a password: \n");
        String password = bufferedReader.readLine();
        */
        String password = "abcdefghijklmnopqrstuvwxyzzxcvbnmasd";
        List<byte[]> key = IEX2Lev.keyGen(256,password,"salt/salt",100000);
        System.out.println("key[0]: " + key.get(0).toString());
        /*
        ArrayList<File> fileList = new ArrayList<File>();
        TextProc.listf(inPath,fileList);
        TextProc.TextProc(false,inPath);
        */
        File[] files = FileUtil.getFiles(inPath);
        ArrayList<File> fileList = new ArrayList<File>();
        for(File file : files){
            fileList.add(file);
        }
        System.out.println("Start encrypted multimap create... ");
        RR2Lev twoLev = RR2Lev.constructEMMParGMM(key.get(0),APOUtil.getObfuscateKeywords(TextExtractPar.lp1,fileList),bigBlock,smallBlock,dataSize);
        APOUtil.erasureEncode(files,inPath,shaPath);
        while(true){
            //System.out.println("Enter a keyword to search: ");
            String keyword = bufferedReader.readLine();
            //String keyword = scanner.nextLine();
            byte[][] token = RR2Lev.token(key.get(0),keyword);
            System.out.println("Results: " + APOUtil.erasureDecode(twoLev.query(token,twoLev.getDictionary(),twoLev.getArray()),shaPath,outPath).toString());
        }
    }
}
