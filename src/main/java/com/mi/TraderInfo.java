package com.mi;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

import net.common.util.BufferUtil;
import net.jctp.*;


public class TraderInfo implements JctpConstants {
    private static Properties configProps = new Properties();
    private static TraderApi traderApi;

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

    public static void main(String[] args)
        throws Throwable
    {
    	System.out.println("Turbo Mode: "+BufferUtil.isTurboModeEnabled());
        File tmpTrader = new File("tmp_traderapi");
        tmpTrader.mkdir();

        InputStream is = TraderInfo.class.getResourceAsStream("config.properties");
        if ( is==null ){
        	System.out.println("Unable to load config.properties from classpath.");
        	System.exit(1);
        }
        configProps = loadConfig();

        String traderFrontUrl = configProps.getProperty("ctp.traderFrontUrl");
        String brokerId = configProps.getProperty("ctp.brokerId");
        String userId = configProps.getProperty("ctp.userId");
        String password = configProps.getProperty("ctp.password");
        String instrumentId = configProps.getProperty("ctp.instrumentId");
        System.out.println("Connecting "+traderFrontUrl+" ... ");

        traderApi = new TraderApi("tmp_traderapi/");
        System.out.println("JCTP TRADERAPI VERSION: "+traderApi.GetApiVersion());
        traderApi.setListener(new TraderListener());
        traderApi.setFlowControl(true);
        traderApi.SubscribePrivateTopic(JctpConstants.THOST_TERT_QUICK);
        traderApi.SubscribePublicTopic(JctpConstants.THOST_TERT_QUICK);
        System.out.println("连接 TraderApi");
        traderApi.SyncConnect(traderFrontUrl);
        Thread.sleep(200);
        {
            System.out.println("TraderApi 连接成功, 登录");
            CThostFtdcReqUserLoginField f = new CThostFtdcReqUserLoginField();
            f.BrokerID = brokerId;
            f.UserID = userId;
            f.Password = password;
            CThostFtdcRspUserLoginField r = traderApi.SyncReqUserLogin(f);
            System.out.println("Front ID: "+r.FrontID+" , Session ID: "+r.SessionID+" , MaxOrderRef : "+r.MaxOrderRef);
        }
        {
            System.out.println("登录成功, 取结算单确认信息");
            CThostFtdcQrySettlementInfoConfirmField qrySettlementInfoConfirmField = new CThostFtdcQrySettlementInfoConfirmField(brokerId, userId, userId, null);
            CThostFtdcSettlementInfoConfirmField settlementInfoConfirmField =
                traderApi.SyncReqQrySettlementInfoConfirm(qrySettlementInfoConfirmField);
            if ( settlementInfoConfirmField==null || !traderApi.GetTradingDay().equals(settlementInfoConfirmField.ConfirmDate) ){
                System.out.println("查询上日结算单");
                CThostFtdcQrySettlementInfoField qrySettlementInfoField = new CThostFtdcQrySettlementInfoField();
                qrySettlementInfoField.BrokerID = brokerId;
                qrySettlementInfoField.AccountID = userId;
                CThostFtdcSettlementInfoField[] settlementInfoFields
                    = traderApi.SyncAllReqQrySettlementInfo(qrySettlementInfoField);
                if ( settlementInfoFields==null || settlementInfoFields.length==0 ){
                    System.out.println("上一交易日无结算");
                }else{
                    CThostFtdcSettlementInfoField f1 = settlementInfoFields[0];
                    byte[][] rawByteArrays = new byte[settlementInfoFields.length][];
                    for(int i=0;i<settlementInfoFields.length;i++)
                        rawByteArrays[i] = settlementInfoFields[i]._rawBytes;

                    System.out.println("交易日 "+f1.TradingDay
                            +" 投资者 "+f1.InvestorID
                            +" 结算号 "+f1.SettlementID
                            +" 流水号 "+f1.SequenceNo);
                    System.out.println( BufferUtil.getStringFromByteArrays(rawByteArrays, JctpConstants.Offset_CThostFtdcSettlementInfoField_Content, JctpConstants.SizeOf_TThostFtdcContentType-1));
                }
                System.out.println("确认结算单");
                settlementInfoConfirmField = new CThostFtdcSettlementInfoConfirmField(brokerId,userId,traderApi.GetTradingDay(),"", 0, null, null);
                settlementInfoConfirmField = traderApi.SyncReqSettlementInfoConfirm(settlementInfoConfirmField);
            }
        }
        {
            System.out.println("查交易所保证金率...");
            CThostFtdcQryExchangeMarginRateField f
                = new CThostFtdcQryExchangeMarginRateField(brokerId,instrumentId.substring(0, 2),JctpConstants.THOST_FTDC_HF_Speculation, null);
            CThostFtdcExchangeMarginRateField rr[] = traderApi.SyncAllReqQryExchangeMarginRate(f);
            for(int i=0;i<rr.length;i++){
            	CThostFtdcExchangeMarginRateField r = rr[i];
	            System.out.println(r.InstrumentID+" 多仓保证金率(金): "+r.LongMarginRatioByMoney+" 多仓保证金率(量): "+r.LongMarginRatioByVolume
	                    +" 空仓保证金率(金): "+r.ShortMarginRatioByMoney+" 空仓保证金率(量): "+r.ShortMarginRatioByVolume);
            }
            Thread.sleep(10);
        }
        {
            System.out.println("查交易所手续费率...");
            CThostFtdcQryInstrumentCommissionRateField f = new CThostFtdcQryInstrumentCommissionRateField(brokerId,userId,instrumentId, null, null);
            CThostFtdcInstrumentCommissionRateField r = traderApi.SyncReqQryInstrumentCommissionRate(f);
            if ( r!=null )
            System.out.println(r.InstrumentID+" 开仓费率(金)： "+r.OpenRatioByMoney+" 开仓费率(量)： "+r.OpenRatioByVolume
                    +" 平仓费率(金)： "+r.CloseRatioByMoney+" 平仓费率(量)： "+r.CloseRatioByVolume
                    +" 今平费率(金)： "+r.CloseTodayRatioByMoney+" 今平费率(量)： "+r.CloseTodayRatioByVolume);
            else
            	System.out.println("交易所无此合约费率");
            Thread.sleep(10);
        }
        {
            System.out.println("查保证金...");
            CThostFtdcQryTradingAccountField q = new CThostFtdcQryTradingAccountField(brokerId, userId, null, THOST_FTDC_BZTP_Future, null);
            CThostFtdcTradingAccountField f = traderApi.SyncReqQryTradingAccount(q);
            System.out.println("总额: "+f.Balance+" 可用: "+f.Available+" 保证金: "+f.CurrMargin);
            Thread.sleep(1);
        }
        {
            System.out.println("查持仓明细...");
            CThostFtdcQryInvestorPositionDetailField f = new CThostFtdcQryInvestorPositionDetailField();
            f.BrokerID = brokerId;
            f.InvestorID = userId;
            CThostFtdcInvestorPositionDetailField[] r= traderApi.SyncAllReqQryInvestorPositionDetail(f);
            for(int i=0;i<r.length;i++){
                CThostFtdcInvestorPositionDetailField d= r[i];
                System.out.println(d.TradeID+" "+d.ExchangeID+" "+(d.Direction==JctpConstants.THOST_FTDC_D_Buy?"多":"空")
                        +" 日 "+d.OpenDate+" 价 "+d.OpenPrice+" 量 "+d.Volume+" 盈 "+d.CloseProfitByDate+" 保 "+d.Margin
                        );
            }
            Thread.sleep(1);
        }
        {
            System.out.println("查持仓信息...");
            CThostFtdcQryInvestorPositionField f = new CThostFtdcQryInvestorPositionField(brokerId,userId,null, null, null);
            CThostFtdcInvestorPositionField[] r= traderApi.SyncAllReqQryInvestorPosition(f);
            if ( r!=null){
                for(int i=0;i<r.length;i++){
                    System.out.println("InstrumentID: "+r[i].InstrumentID+", Amount: "+r[i].OpenAmount+", Volume: "+r[i].OpenVolume+", Cost "+r[i].OpenCost);
                }
            }
            Thread.sleep(1);
        }
        {
            System.out.println("查下单信息...");
            CThostFtdcQryOrderField f = new CThostFtdcQryOrderField();
            f.BrokerID = brokerId; f.InvestorID = userId;
            CThostFtdcOrderField[] r = traderApi.SyncAllReqQryOrder(f);
        }
        boolean submitPriceOrder = "true".equalsIgnoreCase( configProps.getProperty("ctp.submitOrder"));
        double price = Double.parseDouble(configProps.getProperty("ctp.price"));
        if ( submitPriceOrder ){
            submitLimitPriceOrder(
                   brokerId,
                   userId,
                   instrumentId,
                   "0",
                   true,
                   true,
                   1,
                   price
                   );
            //Sleep 3 seconds to wait return
            Thread.sleep(3000);
        }
    }

    private static void submitLimitPriceOrder(
            String brokerId,
            String investorId,
            String instrumentId,
            String orderRef,
            boolean direction,
            boolean open,
            int volume,
            double price
            ){
        CThostFtdcInputOrderField r = new CThostFtdcInputOrderField();
        r.BrokerID = brokerId;
        r.InvestorID = investorId;
        r.InstrumentID = instrumentId;
        r.OrderRef = orderRef;
        r.UserID = investorId;
        r.MinVolume = 1;
        r.ForceCloseReason = JctpConstants.THOST_FTDC_FCC_NotForceClose; //强平原因: 非强平
        r.IsAutoSuspend = false; //自动挂起标志: 不挂起
        r.UserForceClose = false; //用户强评标志: 否
        r.TimeCondition = JctpConstants.THOST_FTDC_TC_GFD; //当日有效
        r.StopPrice = 0; //止损价

        r.OrderPriceType = JctpConstants.THOST_FTDC_OPT_LimitPrice; //限价
        r.LimitPrice = price;
        r.Direction = direction?THOST_FTDC_D_Buy:THOST_FTDC_D_Sell; //买卖标志
        r.CombOffsetFlag = open? STRING_THOST_FTDC_OF_Open: STRING_THOST_FTDC_OF_CloseToday; //开平标志
        r.CombHedgeFlag =  STRING_THOST_FTDC_HF_Speculation; //投机
        r.ContingentCondition = THOST_FTDC_CC_Immediately; //立即触发
        r.VolumeCondition = THOST_FTDC_VC_AV; //任意数量成交
        r.VolumeTotalOriginal = volume; //数量

        try{
            traderApi.ReqOrderInsert(r);
        }catch(Throwable t){
            t.printStackTrace();
        }
    }

}

class TraderListener implements TraderApiListener {

    @Override
    public void OnFrontConnected() {

        System.out.println("TraderListener.OnFrontDisconnected enter");

    }

    @Override
    public void OnFrontDisconnected(int nReason) {

        System.out.println("TraderListener.OnFrontDisconnected enter: "
                + nReason);

    }

    @Override
    public void OnHeartBeatWarning(int nTimeLapse) {
    }

    @Override
    public void OnRspAuthenticate(
            CThostFtdcRspAuthenticateField pRspAuthenticateField,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out.println("TraderListener.OnRspAuthenticate enter: "
                + pRspAuthenticateField + "," + pRspInfo + "," + nRequestID
                + "," + bIsLast);
    }

    @Override
    public void OnRspUserLogin(CThostFtdcRspUserLoginField pRspUserLogin,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out.println("TraderListener.OnRspUserLogin enter: "
                + pRspUserLogin + "," + pRspInfo + "," + nRequestID + ","
                + bIsLast);
    }

    @Override
    public void OnRspSettlementInfoConfirm(
            CThostFtdcSettlementInfoConfirmField pSettlementInfoConfirm,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out.println("TraderListener.OnRspSettlementInfoConfirm enter: "
                + pSettlementInfoConfirm + "," + pRspInfo + "," + nRequestID
                + "," + bIsLast);

    }

    @Override
    public void OnRspUserLogout(CThostFtdcUserLogoutField pUserLogout,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out.println("TraderListener.OnRspUserLogout enter: "
                + pUserLogout + "," + pRspInfo + "," + nRequestID + ","
                + bIsLast);
    }

    @Override
    public void OnRspUserPasswordUpdate(
            CThostFtdcUserPasswordUpdateField pUserPasswordUpdate,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out.println("TraderListener.OnRspUserPasswordUpdate enter: "
                + pUserPasswordUpdate + "," + pRspInfo + "," + nRequestID + ","
                + bIsLast);
    }

    @Override
    public void OnRspTradingAccountPasswordUpdate(
            CThostFtdcTradingAccountPasswordUpdateField pTradingAccountPasswordUpdate,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out
                .println("TraderListener.OnRspTradingAccountPasswordUpdate enter: "
                        + pTradingAccountPasswordUpdate
                        + ","
                        + pRspInfo
                        + ","
                        + nRequestID + "," + bIsLast);
    }

    @Override
    public void OnRspOrderInsert(CThostFtdcInputOrderField pInputOrder,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out.println("TraderListener.OnRspOrderInsert enter: "
                + pInputOrder + "," + pRspInfo + "," + nRequestID + ","
                + bIsLast);
    }

    @Override
    public void OnRspParkedOrderInsert(CThostFtdcParkedOrderField pParkedOrder,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out.println("TraderListener.OnRspParkedOrderInsert enter: "
                + pParkedOrder + "," + pRspInfo + "," + nRequestID + ","
                + bIsLast);
    }

    @Override
    public void OnRspParkedOrderAction(
            CThostFtdcParkedOrderActionField pParkedOrderAction,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out.println("TraderListener.OnRspParkedOrderAction enter: "
                + pParkedOrderAction + "," + pRspInfo + "," + nRequestID + ","
                + bIsLast);
    }

    @Override
    public void OnRspOrderAction(
            CThostFtdcInputOrderActionField pInputOrderAction,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out.println("TraderListener.OnRspOrderAction enter: "
                + pInputOrderAction + "," + pRspInfo + "," + nRequestID + ","
                + bIsLast);
    }

    @Override
    public void OnRspQueryMaxOrderVolume(
            CThostFtdcQueryMaxOrderVolumeField pQueryMaxOrderVolume,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out.println("TraderListener.OnRspQueryMaxOrderVolume enter: "
                + pQueryMaxOrderVolume + "," + pRspInfo + "," + nRequestID
                + "," + bIsLast);
    }

    @Override
    public void OnRspRemoveParkedOrder(
            CThostFtdcRemoveParkedOrderField pRemoveParkedOrder,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out.println("TraderListener.OnRspRemoveParkedOrder enter: "
                + pRemoveParkedOrder + "," + pRspInfo + "," + nRequestID + ","
                + bIsLast);
    }

    @Override
    public void OnRspRemoveParkedOrderAction(
            CThostFtdcRemoveParkedOrderActionField pRemoveParkedOrderAction,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out
                .println("TraderListener.OnRspRemoveParkedOrderAction enter: "
                        + pRemoveParkedOrderAction + "," + pRspInfo + ","
                        + nRequestID + "," + bIsLast);
    }

    @Override
    public void OnRspQryOrder(CThostFtdcOrderField pOrder,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out.println("TraderListener.OnRspQryOrder enter: " + pOrder
                + "," + pRspInfo + "," + nRequestID + "," + bIsLast);
    }

    @Override
    public void OnRspQryTrade(CThostFtdcTradeField pTrade,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out.println("TraderListener.OnRspQryTrade enter: " + pTrade
                + "," + pRspInfo + "," + nRequestID + "," + bIsLast);
    }

    @Override
    public void OnRspQryInvestorPosition(
            CThostFtdcInvestorPositionField pInvestorPosition,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out.println("TraderListener.OnRspQryInvestorPosition enter: "
                + pInvestorPosition + "," + pRspInfo + "," + nRequestID + ","
                + bIsLast);
    }

    @Override
    public void OnRspQryTradingAccount(
            CThostFtdcTradingAccountField pTradingAccount,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out.println("TraderListener.OnRspQryTradingAccount enter: "
                + pTradingAccount + "," + pRspInfo + "," + nRequestID + ","
                + bIsLast);
    }

    @Override
    public void OnRspQryInvestor(CThostFtdcInvestorField pInvestor,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out
                .println("TraderListener.OnRspQryInvestor enter: " + pInvestor
                        + "," + pRspInfo + "," + nRequestID + "," + bIsLast);
    }

    @Override
    public void OnRspQryTradingCode(CThostFtdcTradingCodeField pTradingCode,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out.println("TraderListener.OnRspQryTradingCode enter: "
                + pTradingCode + "," + pRspInfo + "," + nRequestID + ","
                + bIsLast);
    }

    @Override
    public void OnRspQryInstrumentMarginRate(
            CThostFtdcInstrumentMarginRateField pInstrumentMarginRate,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out
                .println("TraderListener.OnRspQryInstrumentMarginRate enter: "
                        + pInstrumentMarginRate + "," + pRspInfo + ","
                        + nRequestID + "," + bIsLast);
    }

    @Override
    public void OnRspQryInstrumentCommissionRate(
            CThostFtdcInstrumentCommissionRateField pInstrumentCommissionRate,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out
                .println("TraderListener.OnRspQryInstrumentCommissionRate enter: "
                        + pInstrumentCommissionRate
                        + ","
                        + pRspInfo
                        + ","
                        + nRequestID + "," + bIsLast);
    }

    @Override
    public void OnRspQryExchange(CThostFtdcExchangeField pExchange,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out
                .println("TraderListener.OnRspQryExchange enter: " + pExchange
                        + "," + pRspInfo + "," + nRequestID + "," + bIsLast);
    }

    @Override
    public void OnRspQryInstrument(CThostFtdcInstrumentField pInstrument,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out.println("TraderListener.OnRspQryInstrument enter: "
                + pInstrument + "," + pRspInfo + "," + nRequestID + ","
                + bIsLast);
    }

    @Override
    public void OnRspQryDepthMarketData(
            CThostFtdcDepthMarketDataField pDepthMarketData,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out.println("TraderListener.OnRspQryDepthMarketData enter: "
                + pDepthMarketData + "," + pRspInfo + "," + nRequestID + ","
                + bIsLast);
    }

    @Override
    public void OnRspQrySettlementInfo(
            CThostFtdcSettlementInfoField pSettlementInfo,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out.println("TraderListener.OnRspQrySettlementInfo enter: "
                + pSettlementInfo + "," + pRspInfo + "," + nRequestID + ","
                + bIsLast);
    }

    @Override
    public void OnRspQryTransferBank(CThostFtdcTransferBankField pTransferBank,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out.println("TraderListener.OnRspQryTransferBank enter: "
                + pTransferBank + "," + pRspInfo + "," + nRequestID + ","
                + bIsLast);
    }

    @Override
    public void OnRspQryInvestorPositionDetail(
            CThostFtdcInvestorPositionDetailField pInvestorPositionDetail,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out
                .println("TraderListener.OnRspQryInvestorPositionDetail enter: "
                        + pInvestorPositionDetail
                        + ","
                        + pRspInfo
                        + ","
                        + nRequestID + "," + bIsLast);
    }

    @Override
    public void OnRspQryNotice(CThostFtdcNoticeField pNotice,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out.println("TraderListener.OnRspQryNotice enter: " + pNotice
                + "," + pRspInfo + "," + nRequestID + "," + bIsLast);
    }

    @Override
    public void OnRspQrySettlementInfoConfirm(
            CThostFtdcSettlementInfoConfirmField pSettlementInfoConfirm,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out
                .println("TraderListener.OnRspQrySettlementInfoConfirm enter: "
                        + pSettlementInfoConfirm + "," + pRspInfo + ","
                        + nRequestID + "," + bIsLast);
    }

    @Override
    public void OnRspQryInvestorPositionCombineDetail(
            CThostFtdcInvestorPositionCombineDetailField pInvestorPositionCombineDetail,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out
                .println("TraderListener.OnRspQryInvestorPositionCombineDetail enter: "
                        + pInvestorPositionCombineDetail
                        + ","
                        + pRspInfo
                        + ","
                        + nRequestID + "," + bIsLast);
    }

    @Override
    public void OnRspQryCFMMCTradingAccountKey(
            CThostFtdcCFMMCTradingAccountKeyField pCFMMCTradingAccountKey,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out
                .println("TraderListener.OnRspQryCFMMCTradingAccountKey enter: "
                        + pCFMMCTradingAccountKey
                        + ","
                        + pRspInfo
                        + ","
                        + nRequestID + "," + bIsLast);
    }

    @Override
    public void OnRspQryEWarrantOffset(
            CThostFtdcEWarrantOffsetField pEWarrantOffset,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out.println("TraderListener.OnRspQryEWarrantOffset enter: "
                + pEWarrantOffset + "," + pRspInfo + "," + nRequestID + ","
                + bIsLast);
    }

    @Override
    public void OnRspQryInvestorProductGroupMargin(
            CThostFtdcInvestorProductGroupMarginField pInvestorProductGroupMargin,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out
                .println("TraderListener.OnRspQryInvestorProductGroupMargin enter: "
                        + pInvestorProductGroupMargin
                        + ","
                        + pRspInfo
                        + ","
                        + nRequestID + "," + bIsLast);
    }

    @Override
    public void OnRspQryExchangeMarginRate(
            CThostFtdcExchangeMarginRateField pExchangeMarginRate,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out.println("TraderListener.OnRspQryExchangeMarginRate enter: "
                + pExchangeMarginRate + "," + pRspInfo + "," + nRequestID + ","
                + bIsLast);
    }

    @Override
    public void OnRspQryExchangeMarginRateAdjust(
            CThostFtdcExchangeMarginRateAdjustField pExchangeMarginRateAdjust,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out
                .println("TraderListener.OnRspQryExchangeMarginRateAdjust enter: "
                        + pExchangeMarginRateAdjust
                        + ","
                        + pRspInfo
                        + ","
                        + nRequestID + "," + bIsLast);
    }

    @Override
    public void OnRspQryTransferSerial(
            CThostFtdcTransferSerialField pTransferSerial,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out.println("TraderListener.OnRspQryTransferSerial enter: "
                + pTransferSerial + "," + pRspInfo + "," + nRequestID + ","
                + bIsLast);
    }

    @Override
    public void OnRspQryAccountregister(
            CThostFtdcAccountregisterField pAccountregister,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out.println("TraderListener.OnRspQryAccountregister enter: "
                + pAccountregister + "," + pRspInfo + "," + nRequestID + ","
                + bIsLast);
    }

    @Override
    public void OnRspError(CThostFtdcRspInfoField pRspInfo, int nRequestID,
            boolean bIsLast) {

        System.out.println("TraderListener.OnRspError enter: " + pRspInfo + ","
                + nRequestID + "," + bIsLast);
    }

    @Override
    public void OnRtnOrder(CThostFtdcOrderField pOrder) {

        System.out.println("TraderListener.OnRtnOrder enter: " + pOrder);
    }

    @Override
    public void OnRtnTrade(CThostFtdcTradeField pTrade) {

        System.out.println("TraderListener.OnRtnTrade enter: " + pTrade);
    }

    @Override
    public void OnErrRtnOrderInsert(CThostFtdcInputOrderField pInputOrder,
            CThostFtdcRspInfoField pRspInfo) {

        System.out.println("TraderListener.OnErrRtnOrderInsert enter: "
                + pInputOrder + "," + pRspInfo);
    }

    @Override
    public void OnErrRtnOrderAction(CThostFtdcOrderActionField pOrderAction,
            CThostFtdcRspInfoField pRspInfo) {

        System.out.println("TraderListener.OnErrRtnOrderAction enter: "
                + pOrderAction + "," + pRspInfo);
    }

    @Override
    public void OnRtnInstrumentStatus(
            CThostFtdcInstrumentStatusField pInstrumentStatus) {
        System.out.println("TraderListener.OnRtnInstrumentStatus enter: "
                + pInstrumentStatus);
    }

    @Override
    public void OnRtnTradingNotice(
            CThostFtdcTradingNoticeInfoField pTradingNoticeInfo) {

        System.out.println("TraderListener.OnRtnTradingNotice enter: "
                + pTradingNoticeInfo);
    }

    @Override
    public void OnRtnErrorConditionalOrder(
            CThostFtdcErrorConditionalOrderField pErrorConditionalOrder) {

        System.out.println("TraderListener.OnRtnErrorConditionalOrder enter: "
                + pErrorConditionalOrder);
    }

    @Override
    public void OnRspQryContractBank(CThostFtdcContractBankField pContractBank,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out.println("TraderListener.OnRspQryContractBank enter: "
                + pContractBank + "," + pRspInfo + "," + nRequestID + ","
                + bIsLast);
    }

    @Override
    public void OnRspQryParkedOrder(CThostFtdcParkedOrderField pParkedOrder,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out.println("TraderListener.OnRspQryParkedOrder enter: "
                + pParkedOrder + "," + pRspInfo + "," + nRequestID + ","
                + bIsLast);
    }

    @Override
    public void OnRspQryParkedOrderAction(
            CThostFtdcParkedOrderActionField pParkedOrderAction,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out.println("TraderListener.OnRspQryParkedOrderAction enter: "
                + pParkedOrderAction + "," + pRspInfo + "," + nRequestID + ","
                + bIsLast);
    }

    @Override
    public void OnRspQryTradingNotice(
            CThostFtdcTradingNoticeField pTradingNotice,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out.println("TraderListener.OnRspQryTradingNotice enter: "
                + pTradingNotice + "," + pRspInfo + "," + nRequestID + ","
                + bIsLast);
    }

    @Override
    public void OnRspQryBrokerTradingParams(
            CThostFtdcBrokerTradingParamsField pBrokerTradingParams,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out.println("TraderListener.OnRspQryBrokerTradingParams enter: "
                + pBrokerTradingParams + "," + pRspInfo + "," + nRequestID
                + "," + bIsLast);
    }

    @Override
    public void OnRspQryBrokerTradingAlgos(
            CThostFtdcBrokerTradingAlgosField pBrokerTradingAlgos,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out.println("TraderListener.OnRspQryBrokerTradingAlgos enter: "
                + pBrokerTradingAlgos + "," + pRspInfo + "," + nRequestID + ","
                + bIsLast);
    }

    @Override
    public void OnRtnFromBankToFutureByBank(
            CThostFtdcRspTransferField pRspTransfer) {

        System.out.println("TraderListener.OnRtnFromBankToFutureByBank enter: "
                + pRspTransfer);
    }

    @Override
    public void OnRtnFromFutureToBankByBank(
            CThostFtdcRspTransferField pRspTransfer) {

        System.out.println("TraderListener.OnRtnFromFutureToBankByBank enter: "
                + pRspTransfer);
    }

    @Override
    public void OnRtnRepealFromBankToFutureByBank(
            CThostFtdcRspRepealField pRspRepeal) {

        System.out
                .println("TraderListener.OnRtnRepealFromBankToFutureByBank enter: "
                        + pRspRepeal);
    }

    @Override
    public void OnRtnRepealFromFutureToBankByBank(
            CThostFtdcRspRepealField pRspRepeal) {

        System.out
                .println("TraderListener.OnRtnRepealFromFutureToBankByBank enter: "
                        + pRspRepeal);
    }

    @Override
    public void OnRtnFromBankToFutureByFuture(
            CThostFtdcRspTransferField pRspTransfer) {

        System.out
                .println("TraderListener.OnRtnFromBankToFutureByFuture enter: "
                        + pRspTransfer);
    }

    @Override
    public void OnRtnFromFutureToBankByFuture(
            CThostFtdcRspTransferField pRspTransfer) {

        System.out
                .println("TraderListener.OnRtnFromFutureToBankByFuture enter: "
                        + pRspTransfer);
    }

    @Override
    public void OnRtnRepealFromBankToFutureByFutureManual(
            CThostFtdcRspRepealField pRspRepeal) {

        System.out
                .println("TraderListener.OnRtnRepealFromBankToFutureByFutureManual enter: "
                        + pRspRepeal);
    }

    @Override
    public void OnRtnRepealFromFutureToBankByFutureManual(
            CThostFtdcRspRepealField pRspRepeal) {

        System.out
                .println("TraderListener.OnRtnRepealFromFutureToBankByFutureManual enter: "
                        + pRspRepeal);
    }

    @Override
    public void OnRtnQueryBankBalanceByFuture(
            CThostFtdcNotifyQueryAccountField pNotifyQueryAccount) {

        System.out
                .println("TraderListener.OnRtnQueryBankBalanceByFuture enter: "
                        + pNotifyQueryAccount);
    }

    @Override
    public void OnErrRtnBankToFutureByFuture(
            CThostFtdcReqTransferField pReqTransfer,
            CThostFtdcRspInfoField pRspInfo) {

        System.out
                .println("TraderListener.OnErrRtnBankToFutureByFuture enter: "
                        + pReqTransfer + "," + pRspInfo);
    }

    @Override
    public void OnErrRtnFutureToBankByFuture(
            CThostFtdcReqTransferField pReqTransfer,
            CThostFtdcRspInfoField pRspInfo) {

        System.out
                .println("TraderListener.OnErrRtnFutureToBankByFuture enter: "
                        + pReqTransfer + "," + pRspInfo);
    }

    @Override
    public void OnErrRtnRepealBankToFutureByFutureManual(
            CThostFtdcReqRepealField pReqRepeal, CThostFtdcRspInfoField pRspInfo) {

        System.out
                .println("TraderListener.OnErrRtnRepealBankToFutureByFutureManual enter: "
                        + pReqRepeal + "," + pRspInfo);
    }

    @Override
    public void OnErrRtnRepealFutureToBankByFutureManual(
            CThostFtdcReqRepealField pReqRepeal, CThostFtdcRspInfoField pRspInfo) {

        System.out
                .println("TraderListener.OnErrRtnRepealFutureToBankByFutureManual enter: "
                        + pReqRepeal + "," + pRspInfo);
    }

    @Override
    public void OnErrRtnQueryBankBalanceByFuture(
            CThostFtdcReqQueryAccountField pReqQueryAccount,
            CThostFtdcRspInfoField pRspInfo) {

        System.out
                .println("TraderListener.OnErrRtnQueryBankBalanceByFuture enter: "
                        + pReqQueryAccount + "," + pRspInfo);
    }

    @Override
    public void OnRtnRepealFromBankToFutureByFuture(
            CThostFtdcRspRepealField pRspRepeal) {

        System.out
                .println("TraderListener.OnRtnRepealFromBankToFutureByFuture enter: "
                        + pRspRepeal);
    }

    @Override
    public void OnRtnRepealFromFutureToBankByFuture(
            CThostFtdcRspRepealField pRspRepeal) {

        System.out
                .println("TraderListener.OnRtnRepealFromFutureToBankByFuture enter: "
                        + pRspRepeal);
    }

    @Override
    public void OnRspFromBankToFutureByFuture(
            CThostFtdcReqTransferField pReqTransfer,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out
                .println("TraderListener.OnRspFromBankToFutureByFuture enter: "
                        + pReqTransfer + "," + pRspInfo + "," + nRequestID
                        + "," + bIsLast);
    }

    @Override
    public void OnRspFromFutureToBankByFuture(
            CThostFtdcReqTransferField pReqTransfer,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out
                .println("TraderListener.OnRspFromFutureToBankByFuture enter: "
                        + pReqTransfer + "," + pRspInfo + "," + nRequestID
                        + "," + bIsLast);
    }

    @Override
    public void OnRspQueryBankAccountMoneyByFuture(
            CThostFtdcReqQueryAccountField pReqQueryAccount,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

        System.out
                .println("TraderListener.OnRspQueryBankAccountMoneyByFuture enter: "
                        + pReqQueryAccount
                        + ","
                        + pRspInfo
                        + ","
                        + nRequestID
                        + "," + bIsLast);
    }

    @Override
    public void OnRtnOpenAccountByBank(CThostFtdcOpenAccountField pOpenAccount) {

        System.out.println("TraderListener.OnRtnOpenAccountByBank enter: "
                + pOpenAccount);
    }

    @Override
    public void OnRtnCancelAccountByBank(
            CThostFtdcCancelAccountField pCancelAccount) {

        System.out.println("TraderListener.OnRtnCancelAccountByBank enter: "
                + pCancelAccount);
    }

    @Override
    public void OnRtnChangeAccountByBank(
            CThostFtdcChangeAccountField pChangeAccount) {

        System.out.println("TraderListener.OnRtnChangeAccountByBank enter: "
                + pChangeAccount);
    }

	@Override
	public void OnRspQryProduct(CThostFtdcProductField pProduct,
			CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {


	}

	@Override
	public void OnRspQryExchangeRate(CThostFtdcExchangeRateField pExchangeRate,
			CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {


	}

	@Override
	public void OnRspQrySecAgentACIDMap(
			CThostFtdcSecAgentACIDMapField pSecAgentACIDMap,
			CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {


	}

	@Override
	public void OnRspExecOrderInsert(
			CThostFtdcInputExecOrderField pInputExecOrder,
			CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {


	}

	@Override
	public void OnRspExecOrderAction(
			CThostFtdcInputExecOrderActionField pInputExecOrderAction,
			CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {


	}

	@Override
	public void OnRspForQuoteInsert(
			CThostFtdcInputForQuoteField pInputForQuote,
			CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {


	}

	@Override
	public void OnRspQuoteInsert(CThostFtdcInputQuoteField pInputQuote,
			CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {


	}

	@Override
	public void OnRspQuoteAction(
			CThostFtdcInputQuoteActionField pInputQuoteAction,
			CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {


	}

	@Override
	public void OnRspQryOptionInstrTradeCost(
			CThostFtdcOptionInstrTradeCostField pOptionInstrTradeCost,
			CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {


	}

	@Override
	public void OnRspQryOptionInstrCommRate(
			CThostFtdcOptionInstrCommRateField pOptionInstrCommRate,
			CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {


	}

	@Override
	public void OnRspQryExecOrder(CThostFtdcExecOrderField pExecOrder,
			CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {


	}

	@Override
	public void OnRspQryForQuote(CThostFtdcForQuoteField pForQuote,
			CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {


	}

	@Override
	public void OnRspQryQuote(CThostFtdcQuoteField pQuote,
			CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {


	}

	@Override
	public void OnRtnExecOrder(CThostFtdcExecOrderField pExecOrder) {


	}

	@Override
	public void OnErrRtnExecOrderInsert(
			CThostFtdcInputExecOrderField pInputExecOrder,
			CThostFtdcRspInfoField pRspInfo) {


	}

	@Override
	public void OnErrRtnExecOrderAction(
			CThostFtdcExecOrderActionField pExecOrderAction,
			CThostFtdcRspInfoField pRspInfo) {


	}

	@Override
	public void OnErrRtnForQuoteInsert(
			CThostFtdcInputForQuoteField pInputExecOrder,
			CThostFtdcRspInfoField pRspInfo) {


	}

	@Override
	public void OnRtnQuote(CThostFtdcQuoteField pQuote) {


	}

	@Override
	public void OnErrRtnQuoteInsert(CThostFtdcInputQuoteField pInputQuote,
			CThostFtdcRspInfoField pRspInfo) {


	}

	@Override
	public void OnErrRtnQuoteAction(CThostFtdcQuoteActionField pQuoteAction,
			CThostFtdcRspInfoField pRspInfo) {


	}

	@Override
	public void OnRtnForQuoteRsp(CThostFtdcForQuoteRspField pForQuoteRsp) {


	}

	@Override
	public void OnRtnCFMMCTradingAccountToken(
			CThostFtdcCFMMCTradingAccountTokenField pCFMMCTradingAccountToken) {

	}

	@Override
	public void OnRspQueryCFMMCTradingAccountToken(
			CThostFtdcQueryCFMMCTradingAccountTokenField pQueryCFMMCTradingAccountToken,
			CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {

	}

	@Override
	public void OnRspCombActionInsert(
			CThostFtdcInputCombActionField pInputCombAction,
			CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {


	}

	@Override
	public void OnRspQryProductExchRate(
			CThostFtdcProductExchRateField pProductExchRate,
			CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {


	}

	@Override
	public void OnRspQryCombInstrumentGuard(
			CThostFtdcCombInstrumentGuardField pCombInstrumentGuard,
			CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {


	}

	@Override
	public void OnRspQryCombAction(CThostFtdcCombActionField pCombAction,
			CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {


	}

	@Override
	public void OnRtnCombAction(CThostFtdcCombActionField pCombAction) {


	}

	@Override
	public void OnErrRtnCombActionInsert(
			CThostFtdcInputCombActionField pInputCombAction,
			CThostFtdcRspInfoField pRspInfo) {


	}

	@Override
	public void OnRspBatchOrderAction(CThostFtdcInputBatchOrderActionField pInputBatchOrderAction,
			CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {


	}

	@Override
	public void OnRspQryProductGroup(CThostFtdcProductGroupField pProductGroup, CThostFtdcRspInfoField pRspInfo,
			int nRequestID, boolean bIsLast) {


	}

	@Override
	public void OnRspQryMMInstrumentCommissionRate(
			CThostFtdcMMInstrumentCommissionRateField pMMInstrumentCommissionRate, CThostFtdcRspInfoField pRspInfo,
			int nRequestID, boolean bIsLast) {


	}

	@Override
	public void OnRspQryMMOptionInstrCommRate(CThostFtdcMMOptionInstrCommRateField pMMOptionInstrCommRate,
			CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {


	}

	@Override
	public void OnRspQryInstrumentOrderCommRate(CThostFtdcInstrumentOrderCommRateField pInstrumentOrderCommRate,
			CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {


	}

	@Override
	public void OnRtnBulletin(CThostFtdcBulletinField pBulletin) {


	}

	@Override
	public void OnErrRtnBatchOrderAction(CThostFtdcBatchOrderActionField pBatchOrderAction,
			CThostFtdcRspInfoField pRspInfo) {


	}

    @Override
    public void OnRspOptionSelfCloseInsert(CThostFtdcInputOptionSelfCloseField pInputOptionSelfClose,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
        // TODO Auto-generated method stub

    }

    @Override
    public void OnRspOptionSelfCloseAction(CThostFtdcInputOptionSelfCloseActionField pInputOptionSelfCloseAction,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
        // TODO Auto-generated method stub

    }

    @Override
    public void OnRspQrySecAgentTradingAccount(CThostFtdcTradingAccountField pTradingAccount,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
        // TODO Auto-generated method stub

    }

    @Override
    public void OnRspQrySecAgentCheckMode(CThostFtdcSecAgentCheckModeField pSecAgentCheckMode,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
        // TODO Auto-generated method stub

    }

    @Override
    public void OnRspQryOptionSelfClose(CThostFtdcOptionSelfCloseField pOptionSelfClose,
            CThostFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
        // TODO Auto-generated method stub

    }

    @Override
    public void OnRspQryInvestUnit(CThostFtdcInvestUnitField pInvestUnit, CThostFtdcRspInfoField pRspInfo,
            int nRequestID, boolean bIsLast) {
        // TODO Auto-generated method stub

    }

    @Override
    public void OnRtnOptionSelfClose(CThostFtdcOptionSelfCloseField pOptionSelfClose) {
        // TODO Auto-generated method stub

    }

    @Override
    public void OnErrRtnOptionSelfCloseInsert(CThostFtdcInputOptionSelfCloseField pInputOptionSelfClose,
            CThostFtdcRspInfoField pRspInfo) {
        // TODO Auto-generated method stub

    }

    @Override
    public void OnErrRtnOptionSelfCloseAction(CThostFtdcOptionSelfCloseActionField pOptionSelfCloseAction,
            CThostFtdcRspInfoField pRspInfo) {
        // TODO Auto-generated method stub

    }

}