package com.mi;

import java.text.*;
import java.util.*;
import java.io.*;
import java.net.URL;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import net.common.util.BufferUtil;
import net.jctp.*;

public class MarketDataSaver {
    
    static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    static final SimpleDateFormat marketDateFormat = new SimpleDateFormat("yyyy-MM-dd");
    
    static String dataFileSuffix;
    static AtomicInteger dataCount = new AtomicInteger();
    static volatile boolean requestStop;
    static File dataDir = new File("data");
    static final BlockingQueue<CThostFtdcDepthMarketDataField> marketDataQueue = new LinkedBlockingDeque<CThostFtdcDepthMarketDataField>();
    static final Map<String,BufferedWriter> dataWriterMap = new HashMap<String,BufferedWriter>();
    
    private static class SaveThread implements Runnable{

        @Override
        public void run() {
            StringBuilder line = new StringBuilder(512);
            while(!requestStop){
                int queueLength = 0;
                CThostFtdcDepthMarketDataField field = null;
                try {
                    field = marketDataQueue.take();
                    queueLength = marketDataQueue.size();
                } catch (InterruptedException e) {}
                if ( field==null )
                    continue;
                try {
                    BufferedWriter writer = dataWriterMap.get(field.InstrumentID);
                    if ( writer==null ){
                        writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(dataDir,field.InstrumentID+"."+dataFileSuffix),true),"UTF-8"));
                        writer.write("\n");
                        dataWriterMap.put(field.InstrumentID, writer);
                    }
                    line.setLength(0);
                    line.append(field.TradingDay)
                        .append(" ").append(field.UpdateTime)
                        .append(".").append(millisec2str(field.UpdateMillisec))
                        .append(",").append(field.InstrumentID)
                        .append(",").append(price2str(field.LastPrice))
                        .append(",").append(field.Volume)
                        .append(",").append(price2str(field.Turnover))
                        .append(",").append(price2str(field.OpenInterest))
                        .append(",").append(price2str(field.BidPrice1))
                        .append(",").append(field.BidVolume1)
                        .append(",").append(price2str(field.AskPrice1))
                        .append(",").append(field.AskVolume1)
                        .append(",").append(price2str(field.BidPrice2))
                        .append(",").append(field.BidVolume2)
                        .append(",").append(price2str(field.AskPrice2))
                        .append(",").append(field.AskVolume2)
                        .append(",").append(price2str(field.BidPrice3))
                        .append(",").append(field.BidVolume3)
                        .append(",").append(price2str(field.AskPrice3))
                        .append(",").append(field.AskVolume3)
                        .append("\n");
                        writer.write(line.toString());
                        if ( queueLength<100 )
                            writer.flush();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            System.out.println("DataSaver Thread exiting...");
            for( BufferedWriter writer:dataWriterMap.values()){
                try {
                    writer.flush();
                    writer.close();
                }catch(Exception e){}
            }
            dataWriterMap.clear();
        }
    }
    
    static final DecimalFormat millisecFormat = new DecimalFormat("000");
    private static String millisec2str(int millisec){
        return millisecFormat.format(millisec);
    }
    
    static final DecimalFormat priceFormat = new DecimalFormat("###########0.0#");
    private static String price2str(double price){
        if ( price==Double.MAX_VALUE||price==Double.NaN )
            return "";
        return priceFormat.format(price);
    }
    
    
    public static void main(String[] args)
        throws Throwable
    {
    	System.out.println("Turbo Mode: "+BufferUtil.isTurboModeEnabled());
        {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
            dataFileSuffix = dateFormat.format(new Date());
        }
        Properties configProps = loadConfig();
        String frontUrl = configProps.getProperty("ctp.mdFrontUrl");
        String brokerId = configProps.getProperty("ctp.brokerId");
        String userId = configProps.getProperty("ctp.userId");
        String password = configProps.getProperty("ctp.password");
        String ids[] = configProps.getProperty("marketDataSaver.instrumentIds").split(",");
        System.out.println("Connecting "+frontUrl+" ... ");
        dataDir.mkdirs();
        SaveThread saver = new SaveThread();
        Thread saverThread = new Thread(saver);
        saverThread.setName("Market data saver thread");
        saverThread.setDaemon(true);
        saverThread.start();
        
        final MdApi mdApi = new MdApi("",false,false);
        System.out.println("JCTP MDAPI VERSION: "+mdApi.GetApiVersion());
        
        Runtime.getRuntime().addShutdownHook(new Thread(){
            public void run(){
                requestStop = true;
                for( BufferedWriter writer:dataWriterMap.values()){
                    try {
                        writer.flush();
                        writer.close();
                    }catch(Exception e){}
                }
                if ( mdApi.isConnected() ){
                    mdApi.Close();
                    try {
                        Thread.sleep(200);
                    } catch (Exception e) {}
                }
            }
        });
        
        mdApi.setListener(new MdApiListener() {
            
            @Override
            public void OnRtnDepthMarketData(CThostFtdcDepthMarketDataField pDepthMarketData) {
                dataCount.incrementAndGet();
                try {
                    marketDataQueue.put(pDepthMarketData);
                } catch (InterruptedException e) {}
            }
            
            @Override
            public void OnRspUserLogout(CThostFtdcUserLogoutField pUserLogout, CThostFtdcRspInfoField pRspInfo, int nRequestID,
                    boolean bIsLast) {
                
            }
            
            @Override
            public void OnRspUserLogin(CThostFtdcRspUserLoginField pRspUserLogin, CThostFtdcRspInfoField pRspInfo,
                    int nRequestID, boolean bIsLast) {
                System.out.println("DataSaver login.");
            }
            
            @Override
            public void OnRspUnSubMarketData(CThostFtdcSpecificInstrumentField pSpecificInstrument,
                    CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                
            }
            
            @Override
            public void OnRspSubMarketData(CThostFtdcSpecificInstrumentField pSpecificInstrument,
                    CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                System.out.println("DataSaver subscribe market data: "+pSpecificInstrument.InstrumentID);
            }
            
            @Override
            public void OnRspError(CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
                System.out.println("DataSaver get error message: "+pRspInfo);
            }
            
            @Override
            public void OnHeartBeatWarning(int nTimeLapse) {
                
            }
            
            @Override
            public void OnFrontDisconnected(int nReason) {
                System.out.println("DataSaver disconnected.");
                requestStop = true;
            }
            
            @Override
            public void OnFrontConnected() {
                System.out.println("DataSaver connected.");
            }

			@Override
			public void OnRspSubForQuoteRsp(
					CThostFtdcSpecificInstrumentField pSpecificInstrument,
					CThostFtdcRspInfoField pRspInfo, int nRequestID,
					boolean bIsLast) {
				
			}

			@Override
			public void OnRspUnSubForQuoteRsp(
					CThostFtdcSpecificInstrumentField pSpecificInstrument,
					CThostFtdcRspInfoField pRspInfo, int nRequestID,
					boolean bIsLast) {
				
			}

            @Override
            public void OnRtnForQuoteRsp(CThostFtdcForQuoteRspField pForQuoteRsp) {
            }
        });
        
        mdApi.SyncConnect(frontUrl, brokerId, userId, password);
        System.out.println("Subscribe market data IDs: "+Arrays.asList(ids));
        mdApi.SubscribeMarketData(ids);
        while(!requestStop){
            Thread.sleep(60*1000);
            if ( mdApi.isLogin() ){
                Date date = new Date();
                int count = dataCount.getAndSet(0);
                System.out.println(dateFormat.format(date)+" Market data receieved: "+count);
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(date);
                int hour = calendar.get(Calendar.HOUR_OF_DAY);
                int minute = calendar.get(Calendar.MINUTE);
                if ( count==0 && hour>=15 ){
                    System.out.println("Market is closed, DataSaver exiting...");
                    requestStop = true;
                }
            }
            if ( requestStop )
                break;
        }
    }

    private static Properties loadConfig() throws IOException{
        Properties configProps = new Properties();
        URL url = ClassLoader.getSystemResource("config.properties");
        if ( url ==null )
            url = ClassLoader.getSystemResource("/config.properties");
        
        if ( url==null ){
            File file = new File("config.properties");
            if ( file.exists() )
                url = file.toURI().toURL();
        }
        if ( url==null ){
            File file = new File("examples/config.properties");
            if ( file.exists() )
                url = file.toURI().toURL();
        }
        if ( url==null ){
            File file = new File("../examples/config.properties");
            if ( file.exists() )
                url = file.toURI().toURL();
        }
        if ( url==null ){
            System.out.println("Unable to load config.properties from classpath or current directory.");
            System.exit(1);
        }
        System.out.println("Load config from: "+url.getFile());
        configProps.load(url.openStream());
        
        return configProps;
    }
}
