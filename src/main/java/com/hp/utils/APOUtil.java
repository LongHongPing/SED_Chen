package com.hp.utils;

import com.backblaze.erasure.ReedSolomon;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;

/**  APOSSE算法实现 */
public class APOUtil {
    private static int m = 8;
    private static int k = 2;
    private static double p = 0.1;
    private static double q = 0.2;

    public static final int BYTES_IN_INT = 4;

    public static void setParam(int m,int k,double p,double q){
        APOUtil.m = m;
        APOUtil.k = k;
        APOUtil.p = p;
        APOUtil.q = q;
    }

    public static Multimap<String,String> getTopCommonKeywords(Multimap<String,String> keywordsMap,int k){
        Multimap<String,String> topCommonKeywords = ArrayListMultimap.create();
        Map<String,Integer> map = new TreeMap<String, Integer>();
        for(String keyword : keywordsMap.keySet()){
            map.put(keyword,keywordsMap.keySet().size());
        }
        List<Map.Entry<String,Integer>> lists = new ArrayList<Map.Entry<String, Integer>>(map.entrySet());
        Collections.sort(lists, new Comparator<Map.Entry<String, Integer>>() {
            public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
                return o2.getValue() - o1.getValue();
            }
        });
        for(Map.Entry<String,Integer> entry : lists){
            topCommonKeywords.putAll(entry.getKey(),keywordsMap.get(entry.getKey()));
            if(k-- == 0){
                break;
            }
        }
        return topCommonKeywords;
    }

    public static Multimap<String,String> getObfuscateKeywords(Multimap<String,String> keywordsMap, ArrayList<File> files){
        Multimap<String,String> obfuscateKeywords = HashMultimap.create();
        Random random = new Random();

        List<String> keywords = new ArrayList<String>(keywordsMap.keySet());
        for(String keyword : keywords){
            for(File file : files){
                if(keywordsMap.get(keyword).contains(file.getName())){
                    for(int i = 0;i < m;i++){
                        if(random.nextDouble() < p){
                            obfuscateKeywords.put(keyword,file.getName() + "." + i);
                        }
                    }
                }else{
                    for(int i = 0;i < m;i++){
                        if(random.nextDouble() < q){
                            obfuscateKeywords.put(keyword,file.getName() + "." + i);
                        }
                    }
                }
            }
        }
        return obfuscateKeywords;
    }

    public static void encodeFile(File file,String inPath,String shaPath) throws Exception{
        int dataShard = k;
        int parityShard = m - k;
        int totalShard = m;

        final int fileSize = (int) file.length();
        final int storeSize = fileSize + BYTES_IN_INT;
        final int shardSize = (storeSize + dataShard - 1) / dataShard;
        final int bufSize = shardSize * dataShard;
        final byte[] byts = new byte[bufSize];

        InputStream is = null;
        OutputStream os = null;

        ByteBuffer.wrap(byts).putInt(fileSize);
        is = new FileInputStream(file);
        int bytsRead = is.read(byts,BYTES_IN_INT,fileSize);
        if(bytsRead != fileSize){
            throw new IOException("Not enough bytes");
        }


        byte[][] shards = new byte[totalShard][shardSize];
        for(int i = 0;i < dataShard;i++){
            System.arraycopy(byts,i*shardSize,shards[i],0,shardSize);
        }
        ReedSolomon reedSolomon = ReedSolomon.create(dataShard,parityShard);
        reedSolomon.encodeParity(shards,0,shardSize);

        for(int i = 0;i < totalShard;i++){
            File outputFile = new File(shaPath + file.getAbsolutePath().substring(inPath.length()) + "." + i);
            outputFile.getParentFile().mkdirs();
            os = new FileOutputStream(outputFile);
            os.write(shards[i]);
        }

        is.close();
        os.close();
    }
    public static void erasureEncode(File[] files,String inPath,String shaPath) throws Exception{
        for(File file : files){
            encodeFile(file,inPath,shaPath);
        }
    }

    public static void decodeFile(String file,Collection<Byte> shardIndex,String shaPath,String outPath) throws Exception{
        int dataShard = k;
        int parityShard = m - k;
        int totalShard = m;

        final byte[][] shards = new byte[totalShard][];
        final boolean[] shardPre = new boolean[totalShard];

        int shardSize = 0;
        int shardCount = 0;

        InputStream is = null;
        OutputStream os = null;

        for(Byte i : shardIndex){
            File shardFile = new File(shaPath,file + "." + i);
            if(shardFile.exists()){
                shardSize = (int) shardFile.length();
                shards[i] = new byte[shardSize];
                shardPre[i] = true;
                shardCount += 1;
                is = new FileInputStream(shardFile);
                is.read(shards[i],0,shardSize);
            }
        }

        if(shardCount < dataShard){
            System.out.println("Not enough shards");
            return;
        }
        for(int i = 0;i < totalShard;i++){
            if(!shardPre[i]){
                shards[i] = new byte[shardSize];
            }
        }

        ReedSolomon reedSolomon = ReedSolomon.create(dataShard,parityShard);
        reedSolomon.decodeMissing(shards,shardPre,0,shardSize);
        byte[] byts = new byte[shardSize*dataShard];
        for(int i = 0;i < dataShard;i++){
            System.arraycopy(shards[i],0,byts,shardSize * i,shardSize);
        }
        int fileSize = ByteBuffer.wrap(byts).getInt();
        File decodeFile = new File(outPath,file);
        os = new FileOutputStream(decodeFile);
        os.write(byts,BYTES_IN_INT,fileSize);

        is.close();
        os.close();
    }
    public static List<String> erasureDecode(List<String> files,String shaPath,String outPath) throws Exception{
        List<String> decodeFiles = new ArrayList<String>();
        Multimap<String,Byte> shardMap = HashMultimap.create();

        for(String file : files){
            int i = file.lastIndexOf('.');
            shardMap.put(file.substring(0,i),Byte.valueOf(file.substring(i + 1)));
        }

        for(String file : shardMap.keySet()){
            if(shardMap.get(file).size() >= k){
                decodeFiles.add(file + shardMap.get(file));
                decodeFile(file,shardMap.get(file),shaPath,outPath);
            }
        }

        return decodeFiles;
    }
}
