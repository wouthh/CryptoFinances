package cryptofinances;

import com.assist.TradeApi;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import static java.util.Objects.isNull;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JTextField;

public class MainScreen extends javax.swing.JFrame {

    private ArrayList<HashMap> cap = new ArrayList<>();
    private ArrayList<HashMap> cap_original = new ArrayList<>();
    private HashMap bitcoinHistory = new HashMap();
    private ArrayList<HashMap> feeData = new ArrayList<>();
    private MathContext mc = new MathContext(8, RoundingMode.HALF_UP);
    private DateFormat df = new SimpleDateFormat("dd.MM.yyyy");
    private DateFormat df2 = new SimpleDateFormat("yyyy-MM-dd");
    private FeeDataScreen fds = null;
    private BigDecimal euro2Dollar = new BigDecimal(0);
    private BigDecimal btc2Dollar = new BigDecimal(0);
    private BigDecimal ars2Dollar = new BigDecimal(0);
    private BigDecimal btc2Ars = new BigDecimal(0);
    private BigDecimal haveHold = new BigDecimal(0);
    private BigDecimal haveMine = new BigDecimal(0);
    private BigDecimal haveTrade = new BigDecimal(0);
    private BigDecimal optimalHold = new BigDecimal(0);
    private BigDecimal optimalMine = new BigDecimal(0);
    private BigDecimal optimalTrade = new BigDecimal(0);
    private BigDecimal diffHold = new BigDecimal(0);
    private BigDecimal diffMine = new BigDecimal(0);
    private BigDecimal diffTrade = new BigDecimal(0);
    private boolean startup;
    private boolean gotFeeData = false;
    private Date earningsDateStart = new Date();
    private BigDecimal earningsHoldStart = new BigDecimal(0);
    private BigDecimal earningsMineStart = new BigDecimal(0);
    private BigDecimal earningsTradeStart = new BigDecimal(0);
    private BigDecimal earningsExchange1Start = new BigDecimal(0);
    private BigDecimal earningsExchange2Start = new BigDecimal(0);
    private BigDecimal earningsExchange3Start = new BigDecimal(0);
    private String currentCurrency = "eur";
    private int currentConv = 1;
    private String selectedCoins = "";

    public MainScreen() {
        startup = true;
        initComponents();
        SSLUtilities.trustAllHostnames();
        SSLUtilities.trustAllHttpsCertificates();
        Dimension dimension = Toolkit.getDefaultToolkit().getScreenSize();
        int x = (int) ((dimension.getWidth() - this.getWidth()) / 2);
        int y = (int) ((dimension.getHeight() - this.getHeight()) / 2);
        this.setLocation(x, y);
        loadHoldingData(true);
        getCaps(true);
        calcCaps();
        printCaps();
        loadHoldingData(false);
        printPortofolio();
        readHistoricalBitcoinData("https://api.bitcoinvenezuela.com/historical/?pair=btcusd");
        loadMiningData();
        boolean saveTradeBalance = loadTradingData();
        loadDashboardData();
        loadStatData();
        updateStats();
        startup = false;
        if (saveTradeBalance) {
            autosaveTradingData();
        }
    }

    private void getCaps(boolean everything) {
        try {
            if (everything) {
                readFromCoinMarketCapArs("https://api.coinmarketcap.com/v1/ticker/?convert=ARS&limit=1");
            }
            readFromCoinMarketCap("https://api.coinmarketcap.com/v1/ticker/?convert=EUR&limit=0", everything);
        } catch (IOException ex) {
            Logger.getLogger(MainScreen.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void calcCaps() {
        double total = 0.0;
        ArrayList<HashMap> toRemove = new ArrayList<>();
        // Remove USDT as it's the equivalent of USD
        for (int i = 0; i < cap.size(); i++) {
            if (!cap.get(i).get("symbol").equals("USDT")) {
                total += Double.parseDouble(String.valueOf(cap.get(i).get("market_cap_usd")));
            } else {
                toRemove.add(cap.get(i));
            }
        }
        for (int i = 0; i < toRemove.size(); i++) {
            cap.remove(toRemove.get(i));
        }
        NumberFormat formatter = NumberFormat.getCurrencyInstance();
        String finalAmount = formatter.format(total);
        coinMarketCapsArea.setText("Total cap: " + finalAmount.substring(0, finalAmount.length() - 3) + System.lineSeparator());
        for (int i = 0; i < cap.size(); i++) {
            double amount = Double.parseDouble(String.valueOf(cap.get(i).get("market_cap_usd")));
            amount *= 100000.0;
            amount /= total;
            amount = Math.round(amount);
            amount /= 1000.0;
            cap.get(i).put("share", amount);
        }
    }

    private void printCaps() {
        for (int i = 0; i < cap.size(); i++) {
            double amount = Double.parseDouble(String.valueOf(cap.get(i).get("market_cap_usd")));
            NumberFormat formatter = NumberFormat.getCurrencyInstance();
            String finalAmount = formatter.format(amount);
            coinMarketCapsArea.setText(coinMarketCapsArea.getText() + "#" + cap.get(i).get("rank") + " " + cap.get(i).get("name") + ": " + finalAmount.substring(0, finalAmount.length() - 3) + " (" + cap.get(i).get("share") + "%)" + System.lineSeparator());
        }
    }

    private void loadHoldingData(boolean first) {
        FileInputStream fi = null;
        ObjectInputStream oi = null;
        try {
            fi = new FileInputStream(new File("holding.dat"));
            oi = new ObjectInputStream(fi);
            HashMap loaded = (HashMap) oi.readObject();
            if (!first) {
                ArrayList<HashMap> temp = (ArrayList<HashMap>) loaded.get("cap");
                for (int i = 0; i < temp.size(); i++) {
                    for (int j = 0; j < cap.size(); j++) {
                        if (((String) temp.get(i).get("name")).equals((String) cap.get(j).get("name"))) {
                            cap.get(j).put("amount", temp.get(i).get("amount"));
                            break;
                        }
                    }
                }
                selectedCoins = selectCoins();
                String[] coins = selectedCoins.split(", ");
                for (int i = 0; i < coins.length; i++) {
                    this.coinComboBox.addItem(coins[i]);
                }
            } else {
                this.enterPercentage.setText((String) loaded.getOrDefault("enterPercentage", "2.00"));
                this.exitPercentage.setText((String) loaded.getOrDefault("exitPercentage", "0.50"));
            }
        } catch (FileNotFoundException ex) {

        } catch (IOException ex) {

        } catch (ClassNotFoundException ex) {
            Logger.getLogger(MainScreen.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                if (!isNull(oi)) {
                    oi.close();
                }
                if (!isNull(fi)) {
                    fi.close();
                }
            } catch (IOException ex) {

            }
        }
    }

    private String selectCoins() {
        //return string and modify caps: delete all < threshold marketcap where amount = 0
        String formatted = "";
        BigDecimal marketCap = BigDecimal.ZERO;
        ArrayList<HashMap> toRemove = new ArrayList<>();
        for (int i = 0; i < cap.size(); i++) {
            marketCap = marketCap.add(new BigDecimal(cap.get(i).get("market_cap_usd").toString()));
            cap.get(i).put("toDeleteFlag", false);
        }
        BigDecimal minThreshold_enter = marketCap.divide(new BigDecimal(100), mc).multiply(new BigDecimal(this.enterPercentage.getText()), mc);
        BigDecimal minThreshold_exit = marketCap.divide(new BigDecimal(100), mc).multiply(new BigDecimal(this.exitPercentage.getText()), mc);
        for (int i = 0; i < cap.size(); i++) {
            BigDecimal marketCapCoin = new BigDecimal(cap.get(i).get("market_cap_usd").toString());
            if (marketCapCoin.compareTo(minThreshold_enter) == -1) {
                if (new BigDecimal(cap.get(i).get("amount").toString()).compareTo(BigDecimal.ZERO) <= 0) {
                    toRemove.add(cap.get(i));
                } else {
                    if (marketCapCoin.compareTo(minThreshold_exit) == -1) {
                        cap.get(i).put("toDeleteFlag", true);
                    }
                }
            }
        }
        for (int i = 0; i < toRemove.size(); i++) {
            cap.remove(toRemove.get(i));
        }
        for (int i = 0; i < cap.size(); i++) {
            formatted += cap.get(i).get("name").toString();
            if (i < (cap.size() - 1)) {
                formatted += ", ";
            }
        }
        return formatted;
    }

    private void printPortofolio() {
        this.coinPortefolioArea.setText("");
        BigDecimal totalCap = new BigDecimal(0);
        haveHold = new BigDecimal(0);
        for (int i = 0; i < cap.size(); i++) {
            if (!Boolean.parseBoolean(cap.get(i).get("toDeleteFlag").toString())) {
                totalCap = totalCap.add(new BigDecimal(String.valueOf(cap.get(i).get("market_cap_usd"))));
            }
            haveHold = haveHold.add((new BigDecimal(String.valueOf(cap.get(i).get("amount")))).multiply(new BigDecimal(String.valueOf(cap.get(i).get("price_usd"))), mc));
        }
        this.holdingHave.setText(formatBigDecimal(haveHold, "$", 2));
        this.coinPortefolioArea.setText("Your total amount of assets: " + formatBigDecimal(haveHold, "$", 2) + System.lineSeparator());
        for (int i = 0; i < cap.size(); i++) {
            BigDecimal have = new BigDecimal(String.valueOf(cap.get(i).get("amount")));
            BigDecimal need = new BigDecimal(String.valueOf(cap.get(i).get("market_cap_usd")));
            if (Boolean.parseBoolean(cap.get(i).get("toDeleteFlag").toString())) {
                need = BigDecimal.ZERO;
            }
            need = need.divide(totalCap, mc);
            need = need.multiply(haveHold);
            need = need.divide(new BigDecimal(String.valueOf(cap.get(i).get("price_usd"))), mc);
            BigDecimal diff = need.subtract(have, mc);
            this.coinPortefolioArea.setText(this.coinPortefolioArea.getText() + "#" + (i + 1) + " " + cap.get(i).get("name") + " ($" + cap.get(i).get("price_usd") + ")" + ":" + System.lineSeparator() + "You have: " + have.toPlainString() + " " + cap.get(i).get("symbol") + " (" + formatBigDecimal(have.multiply(new BigDecimal(cap.get(i).get("price_usd").toString())), "$", 2) + ")" + System.lineSeparator() + "You need: " + need.toPlainString() + " " + cap.get(i).get("symbol") + " (" + formatBigDecimal(need.multiply(new BigDecimal(cap.get(i).get("price_usd").toString())), "$", 2) + ")" + System.lineSeparator() + "Difference: " + diff.toPlainString() + " " + cap.get(i).get("symbol"));
            try {
                this.coinPortefolioArea.setText(this.coinPortefolioArea.getText() + " (" + formatBigDecimal(diff.multiply(new BigDecimal(cap.get(i).get("price_usd").toString())), "$", 2) + " - " + formatBigDecimal(need.divide(have, mc).multiply(new BigDecimal(100)).subtract(new BigDecimal(100)), "%", 2) + ")");
            } catch (Exception e) {

            }
            this.coinPortefolioArea.setText(this.coinPortefolioArea.getText() + System.lineSeparator());
        }
        this.coinPortefolioArea.setCaretPosition(0);
        updateDashboard(false);
    }

    private void loadMiningData() {
        FileInputStream fi = null;
        ObjectInputStream oi = null;
        try {
            fi = new FileInputStream(new File("mining.dat"));
            oi = new ObjectInputStream(fi);
            HashMap loaded = (HashMap) oi.readObject();
            for (int i = 0; i < this.contractDataTable.getRowCount(); i++) {
                if (loaded.containsKey("contractData_priceContract_" + i)) {
                    this.contractDataTable.getModel().setValueAt(loaded.get("contractData_priceContract_" + i), i, 1);
                }
                if (loaded.containsKey("contractData_hashpowerContract_" + i)) {
                    this.contractDataTable.getModel().setValueAt(loaded.get("contractData_hashpowerContract_" + i), i, 2);
                }
                if (loaded.containsKey("contractData_days_" + i)) {
                    this.contractDataTable.getModel().setValueAt(loaded.get("contractData_days_" + i), i, 3);
                }
                if (loaded.containsKey("contractData_moneyIn_" + i)) {
                    this.contractDataTable.getModel().setValueAt(loaded.get("contractData_moneyIn_" + i), i, 5);
                }
                if (loaded.containsKey("contractData_yourHashpower_" + i)) {
                    this.contractDataTable.getModel().setValueAt(loaded.get("contractData_yourHashpower_" + i), i, 6);
                }
                if (loaded.containsKey("contractData_lastPayout_" + i)) {
                    this.contractDataTable.getModel().setValueAt(loaded.get("contractData_lastPayout_" + i), i, 7);
                }
                if (loaded.containsKey("contractData_currency_" + i)) {
                    this.contractDataTable.getModel().setValueAt(loaded.get("contractData_currency_" + i), i, 8);
                }
                if (loaded.containsKey("contractData_isAvailable_" + i)) {
                    this.contractDataTable.getModel().setValueAt(Boolean.parseBoolean(loaded.get("contractData_isAvailable_" + i).toString()), i, 10);
                }
            }
            this.miningWalletBTC.setText(loaded.getOrDefault("miningWalletBTC", "0.00000000").toString());
            for (int i = 0; i < cap.size(); i++) {
                if (cap.get(i).get("name").toString().equals("Bitcoin")) {
                    this.miningWalletUSD.setText(formatBigDecimal(new BigDecimal(this.miningWalletBTC.getText()).multiply(new BigDecimal(cap.get(i).get("price_usd").toString()), mc), "", 2));
                    break;
                }
            }

            //Remove old feedata
            if (gotFeeData) {
                feeData = (ArrayList<HashMap>) loaded.getOrDefault("feeData", new ArrayList<>());
                ArrayList<HashMap> oldFeeData = new ArrayList<>();
                for (int i = 0; i < feeData.size(); i++) {
                    String timeAsDate = df2.format((new Date()).getTime() - Long.parseLong(feeData.get(i).get("days").toString()) * 86400000L);
                    boolean foundPrice = false;
                    int counter = 0;
                    do {
                        if (!bitcoinHistory.containsKey(timeAsDate)) {
                            counter++;
                            timeAsDate = df2.format((new Date()).getTime() - (Long.parseLong(feeData.get(i).get("days").toString()) - counter) * 86400000L);
                        } else {
                            foundPrice = true;
                        }
                    } while (!foundPrice);
                    String d = bitcoinHistory.get(timeAsDate).toString();
                    feeData.get(i).put("price_btc", d);
                    if (df.parse(feeData.get(i).get("startDate").toString()).getTime() + Long.parseLong(feeData.get(i).get("days").toString()) * 86400000L < new Date().getTime()) {
                        oldFeeData.add(feeData.get(i));
                    }
                }
                for (int i = 0; i < oldFeeData.size(); i++) {
                    feeData.remove(oldFeeData.get(i));
                }
            }
        } catch (IOException ex) {

        } catch (ClassNotFoundException | ParseException ex) {
            Logger.getLogger(MainScreen.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                if (!isNull(oi)) {
                    oi.close();
                }
                if (!isNull(fi)) {
                    fi.close();
                }
            } catch (IOException ex) {

            }
        }
        if (gotFeeData) {
            calculateFees();
            boolean setContactPrice = true;
            for (int i = 0; i < this.contractDataTable.getRowCount(); i++) {
                if (Integer.parseInt(this.contractDataTable.getModel().getValueAt(i, 3).toString()) > 0) {
                    this.contractTypeComboBox.addItem(this.contractDataTable.getModel().getValueAt(i, 0).toString());
                    if (setContactPrice) {
                        try {
                            this.contractPrice.setText(this.contractDataTable.getModel().getValueAt(i, 1).toString());
                            this.contractDays.setText(this.contractDataTable.getModel().getValueAt(i, 3).toString());
                            String timeAsDate = df2.format((new Date()).getTime() - Long.parseLong(feeData.get(i).get("days").toString()) * 86400000L);
                            boolean foundPrice = false;
                            int counter = 0;
                            do {
                                if (!bitcoinHistory.containsKey(timeAsDate)) {
                                    counter++;
                                    timeAsDate = df2.format((new Date()).getTime() - (Long.parseLong(feeData.get(i).get("days").toString()) - counter) * 86400000L);
                                } else {
                                    foundPrice = true;
                                }
                            } while (!foundPrice);
                            this.contractBitcoinPrice.setText(bitcoinHistory.get(timeAsDate).toString());
                            setContactPrice = false;
                        } catch (Exception e) {
                            break;
                        }
                    }
                }
            }
            this.contractStartDate.setText(df.format(new Date()));
        }
        updateMiningTotal();
        calculateWhatToBuyMining();
    }

    public void calculateFees() {
        for (int i = 0; i < this.contractDataTable.getRowCount(); i++) {
            this.contractDataTable.getModel().setValueAt("0.00000000", i, 4);
        }
        for (int i = 0; i < feeData.size(); i++) {
            for (int j = 0; j < this.contractDataTable.getRowCount(); j++) {
                if (this.contractDataTable.getModel().getValueAt(j, 0).toString().equals(feeData.get(i).get("type").toString())) {
                    BigDecimal fee = new BigDecimal(feeData.get(i).get("price_usd").toString());
                    fee = fee.divide(new BigDecimal(feeData.get(i).get("days").toString()), mc);
                    fee = fee.divide(new BigDecimal(feeData.get(i).get("price_btc").toString()), mc);
                    fee = fee.add(new BigDecimal(this.contractDataTable.getModel().getValueAt(j, 4).toString()));
                    this.contractDataTable.getModel().setValueAt(fee.toPlainString(), j, 4);
                }
            }
        }
        for (int i = 0; i < this.contractDataTable.getRowCount(); i++) {
            BigDecimal truePayout = new BigDecimal(this.contractDataTable.getModel().getValueAt(i, 7).toString());
            BigDecimal currencyPrice = new BigDecimal(0);
            for (int j = 0; j < cap.size(); j++) {
                if (cap.get(j).get("symbol").toString().equals(this.contractDataTable.getModel().getValueAt(i, 8).toString())) {
                    currencyPrice = new BigDecimal(cap.get(j).get("price_btc").toString());
                    break;
                }
            }
            truePayout = truePayout.multiply(currencyPrice, mc);
            BigDecimal fee = new BigDecimal(this.contractDataTable.getModel().getValueAt(i, 4).toString());
            truePayout = truePayout.subtract(fee, mc);
            this.contractDataTable.getModel().setValueAt(truePayout.toPlainString(), i, 9);
        }
    }

    private void updateMiningTotal() {
        haveMine = new BigDecimal(0);
        BigDecimal totalPayout = new BigDecimal(0);
        for (int i = 0; i < this.contractDataTable.getRowCount(); i++) {
            haveMine = haveMine.add(new BigDecimal(this.contractDataTable.getModel().getValueAt(i, 5).toString()));
            totalPayout = totalPayout.add(new BigDecimal(this.contractDataTable.getModel().getValueAt(i, 7).toString()));
        }
        for (int j = 0; j < cap.size(); j++) {
            if (cap.get(j).get("name").toString().equals("Bitcoin")) {
                totalPayout = totalPayout.multiply(new BigDecimal(cap.get(j).get("price_usd").toString()));
                break;
            }
        }
        haveMine = haveMine.add(new BigDecimal(this.miningWalletUSD.getText()));
        this.miningHave.setText(formatBigDecimal(haveMine, "$", 2));
        this.totalInMining.setText("Total in mining: " + formatBigDecimal(haveMine, "$", 2));
        this.miningPayoutWas.setText("Complete payout was: " + formatBigDecimal(totalPayout, "$", 2));
        updateDashboard(false);
    }

    private void calculateWhatToBuyMining() {
        if (gotFeeData) {
            int step = 1;
            ArrayList<Integer> availableContracts = new ArrayList<>();
            DefaultListModel dlm = new DefaultListModel();
            BigDecimal total = new BigDecimal(0);
            for (int i = 0; i < this.contractDataTable.getRowCount(); i++) {
                if (Boolean.parseBoolean(this.contractDataTable.getModel().getValueAt(i, 10).toString())) {
                    if (new BigDecimal(this.contractDataTable.getModel().getValueAt(i, 9).toString()).compareTo(BigDecimal.ZERO) > -1) {
                        availableContracts.add(i);
                    }
                }
            }
            try {
                if (!availableContracts.isEmpty()) {
                    BigDecimal money = new BigDecimal(this.miningWalletUSD.getText());
                    for (int i = 0; i < availableContracts.size(); i++) {
                        if (new BigDecimal(this.contractDataTable.getModel().getValueAt(availableContracts.get(i), 5).toString()).compareTo(BigDecimal.ZERO) == 0) {
                            if (new BigDecimal(this.contractDataTable.getModel().getValueAt(availableContracts.get(i), 1).toString()).compareTo(money) < 1) {
                                dlm.addElement("#" + step + " -  " + this.contractDataTable.getModel().getValueAt(availableContracts.get(i), 0).toString() + "  x1  $" + this.contractDataTable.getModel().getValueAt(availableContracts.get(i), 1).toString());
                                step++;
                                money = money.subtract(new BigDecimal(this.contractDataTable.getModel().getValueAt(availableContracts.get(i), 1).toString()));
                                total = total.add(new BigDecimal(this.contractDataTable.getModel().getValueAt(availableContracts.get(i), 1).toString()));
                            }
                        }
                    }
                    availableContracts.clear();
                    for (int i = 0; i < this.contractDataTable.getRowCount(); i++) {
                        if (Boolean.parseBoolean(this.contractDataTable.getModel().getValueAt(i, 10).toString()) && new BigDecimal(this.contractDataTable.getModel().getValueAt(i, 9).toString()).compareTo(BigDecimal.ZERO) == 1) {
                            availableContracts.add(i);
                        }
                    }
                    if (!availableContracts.isEmpty()) {
                        if (availableContracts.size() > 1) {
                            boolean noMoneyLeft = false;
                            int substep = 1;
                            int previousWinner = -1;
                            HashMap contractTemp = new HashMap();
                            for (int i = 0; i < availableContracts.size(); i++) {
                                contractTemp.put(availableContracts.get(i) + "-" + 5, this.contractDataTable.getModel().getValueAt(availableContracts.get(i), 5).toString());
                                contractTemp.put(availableContracts.get(i) + "-" + 6, this.contractDataTable.getModel().getValueAt(availableContracts.get(i), 6).toString());
                                contractTemp.put(availableContracts.get(i) + "-" + 7, this.contractDataTable.getModel().getValueAt(availableContracts.get(i), 7).toString());
                            }
                            do {
                                ArrayList<Integer> availableTemp = new ArrayList<>();
                                for (int i = 0; i < availableContracts.size(); i++) {
                                    availableTemp.add(availableContracts.get(i));
                                }
                                BigDecimal f1 = BigDecimal.ZERO;
                                BigDecimal f2 = BigDecimal.ZERO;
                                do {
                                    f1 = new BigDecimal(contractTemp.get(availableTemp.get(0) + "-" + 7).toString());
                                    f1 = f1.divide(new BigDecimal(contractTemp.get(availableTemp.get(0) + "-" + 6).toString()), mc);
                                    f1 = f1.multiply(new BigDecimal(this.contractDataTable.getModel().getValueAt(availableTemp.get(0), 2).toString()), mc);
                                    BigDecimal f1b = new BigDecimal(contractTemp.get(availableTemp.get(1) + "-" + 7).toString());
                                    f1b = f1b.divide(new BigDecimal(contractTemp.get(availableTemp.get(1) + "-" + 6).toString()), mc);
                                    f1b = f1b.multiply(new BigDecimal(this.contractDataTable.getModel().getValueAt(availableTemp.get(1), 2).toString()), mc);
                                    f1 = f1.divide(f1b, mc);
                                    f2 = new BigDecimal(contractTemp.get(availableTemp.get(0) + "-" + 5).toString());
                                    f2 = f2.divide(new BigDecimal(contractTemp.get(availableTemp.get(1) + "-" + 5).toString()), mc);
                                    if (f1.compareTo(f2) == 1) {
                                        availableTemp.remove(1);
                                    } else {
                                        availableTemp.remove(0);
                                    }
                                } while (availableTemp.size() > 1);
                                //System.out.println("f1: " + f1.toPlainString() + "\t f2: " + f2.toPlainString());
                                if (money.subtract(new BigDecimal(this.contractDataTable.getModel().getValueAt(availableTemp.get(0), 1).toString())).compareTo(BigDecimal.ZERO) > -1) {
                                    money = money.subtract(new BigDecimal(this.contractDataTable.getModel().getValueAt(availableTemp.get(0), 1).toString()));
                                    contractTemp.put(availableTemp.get(0) + "-" + 5, (new BigDecimal(contractTemp.get(availableTemp.get(0) + "-" + 5).toString())).add(new BigDecimal(this.contractDataTable.getModel().getValueAt(availableTemp.get(0), 1).toString())).toPlainString());
                                    contractTemp.put(availableTemp.get(0) + "-" + 7, (new BigDecimal(contractTemp.get(availableTemp.get(0) + "-" + 7).toString())).divide(new BigDecimal(contractTemp.get(availableTemp.get(0) + "-" + 6).toString()), mc).multiply(new BigDecimal(contractTemp.get(availableTemp.get(0) + "-" + 6).toString()).add(new BigDecimal(this.contractDataTable.getModel().getValueAt(availableTemp.get(0), 2).toString()))).toPlainString());
                                    contractTemp.put(availableTemp.get(0) + "-" + 6, (new BigDecimal(contractTemp.get(availableTemp.get(0) + "-" + 6).toString())).add(new BigDecimal(this.contractDataTable.getModel().getValueAt(availableTemp.get(0), 2).toString())).toPlainString());
                                    if (previousWinner == availableTemp.get(0)) {
                                        substep++;
                                    } else if (previousWinner > -1) {
                                        dlm.addElement("#" + step + " -  " + this.contractDataTable.getModel().getValueAt(availableTemp.get(0), 0).toString() + "  x" + substep + "  $" + new BigDecimal(this.contractDataTable.getModel().getValueAt(availableTemp.get(0), 1).toString()).multiply(new BigDecimal(substep)).toPlainString());
                                        step++;
                                        total = total.add(new BigDecimal(this.contractDataTable.getModel().getValueAt(availableTemp.get(0), 1).toString()).multiply(new BigDecimal(substep)));
                                        substep = 1;
                                        previousWinner = availableTemp.get(0);
                                    } else {
                                        previousWinner = availableTemp.get(0);
                                    }
                                } else {
                                    if (previousWinner > -1) {
                                        dlm.addElement("#" + step + " -  " + this.contractDataTable.getModel().getValueAt(availableTemp.get(0), 0).toString() + "  x" + substep + "  $" + new BigDecimal(this.contractDataTable.getModel().getValueAt(availableTemp.get(0), 1).toString()).multiply(new BigDecimal(substep)).toPlainString());
                                        total = total.add(new BigDecimal(this.contractDataTable.getModel().getValueAt(availableTemp.get(0), 1).toString()).multiply(new BigDecimal(substep)));
                                        step++;
                                    }
                                    noMoneyLeft = true;
                                }
                            } while (!noMoneyLeft);
                        } else {
                            MathContext m = new MathContext(4, RoundingMode.FLOOR);
                            int amount = money.divide(new BigDecimal(this.contractDataTable.getModel().getValueAt(availableContracts.get(0), 1).toString()), m).setScale(0, RoundingMode.FLOOR).intValue();
                            if (amount > 0) {
                                BigDecimal subtotal = new BigDecimal(this.contractDataTable.getModel().getValueAt(availableContracts.get(0), 1).toString()).multiply(new BigDecimal(amount));
                                dlm.addElement("#" + step + " -  " + this.contractDataTable.getModel().getValueAt(availableContracts.get(0), 0).toString() + "  x" + amount + "  $" + subtotal.toPlainString());
                                step++;
                                total = total.add(subtotal);
                            }
                        }
                    }
                }
            } catch (Exception e) {

            }
            if (dlm.isEmpty()) {
                dlm.addElement("Nothing interesting found.");
                dlm.addElement("Wait until new interesting contracts are available or exchange prices change.");
            } else {
                dlm.addElement("= = = = = = = = = =");
                dlm.addElement("Total: $" + total.toPlainString());
                dlm.addElement("Left : $" + new BigDecimal(this.miningWalletUSD.getText()).subtract(total).toPlainString());
            }
            this.miningBuyList.setModel(dlm);
        }
    }

    private boolean loadTradingData() {
        FileInputStream fi = null;
        ObjectInputStream oi = null;
        try {
            fi = new FileInputStream(new File("trading.dat"));
            oi = new ObjectInputStream(fi);
            HashMap loaded = (HashMap) oi.readObject();
            this.exchange1In.setText(loaded.get("exchange1").toString());
            this.exchange2In.setText(loaded.get("exchange2").toString());
            this.exchange3In.setText(loaded.get("exchange3").toString());
            this.exchange1Dist.setValue(loaded.get("dist1"));
            this.exchange2Dist.setValue(loaded.get("dist2"));
            this.exchange3Dist.setValue(loaded.get("dist3"));
            this.exchange1ApiKey.setText(loaded.getOrDefault("exchange1ApiKey", "").toString());
            this.exchange2ApiKey.setText(loaded.getOrDefault("exchange2ApiKey", "").toString());
            this.exchange3ApiKey.setText(loaded.getOrDefault("exchange3ApiKey", "").toString());
            this.exchange1ApiSecret.setText(loaded.getOrDefault("exchange1ApiSecret", "").toString());
            this.exchange2ApiSecret.setText(loaded.getOrDefault("exchange2ApiSecret", "").toString());
            this.exchange3ApiSecret.setText(loaded.getOrDefault("exchange3ApiSecret", "").toString());
        } catch (FileNotFoundException ex) {

        } catch (IOException ex) {

        } catch (ClassNotFoundException ex) {
            Logger.getLogger(MainScreen.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                if (!isNull(oi)) {
                    oi.close();
                }
                if (!isNull(fi)) {
                    fi.close();
                }
            } catch (IOException ex) {

            }
        }
        boolean loadedFromNet1 = false;
        boolean loadedFromNet2 = false;
        boolean loadedFromNet3 = false;
        int nb_try = 0;
        do {
            loadedFromNet1 = connectToExchange(1);
            nb_try++;
        } while ((!loadedFromNet1) && (nb_try < 5));
        nb_try = 0;
        do {
            loadedFromNet2 = connectToExchange(2);
            nb_try++;
        } while ((!loadedFromNet2) && (nb_try < 5));
        nb_try = 0;
        do {
            loadedFromNet3 = connectToExchange(3);
            nb_try++;
        } while ((!loadedFromNet3) && (nb_try < 5));
        updateTrade();
        return (loadedFromNet1 || loadedFromNet2 || loadedFromNet3);
    }

    private void updateTrade() {
        haveTrade = new BigDecimal(0);
        haveTrade = haveTrade.add(new BigDecimal(this.exchange1In.getText()));
        haveTrade = haveTrade.add(new BigDecimal(this.exchange2In.getText()));
        haveTrade = haveTrade.add(new BigDecimal(this.exchange3In.getText()));
        this.totalTrading.setText(formatBigDecimal(haveTrade, "$", 2));
        this.tradingHave.setText(formatBigDecimal(haveTrade, "$", 2));

        BigDecimal totalDist = new BigDecimal((int) this.exchange1Dist.getValue() + (int) this.exchange2Dist.getValue() + (int) this.exchange3Dist.getValue());
        if (totalDist.compareTo(BigDecimal.ZERO) == 0) {
            totalDist = new BigDecimal(1);
        }
        BigDecimal opt1 = haveTrade.divide(totalDist, mc).multiply(new BigDecimal((int) this.exchange1Dist.getValue()), mc);
        BigDecimal opt2 = haveTrade.divide(totalDist, mc).multiply(new BigDecimal((int) this.exchange2Dist.getValue()), mc);
        BigDecimal opt3 = haveTrade.divide(totalDist, mc).multiply(new BigDecimal((int) this.exchange3Dist.getValue()), mc);
        this.exchange1Opt.setText(formatBigDecimal(opt1, "$", 2));
        this.exchange2Opt.setText(formatBigDecimal(opt2, "$", 2));
        this.exchange3Opt.setText(formatBigDecimal(opt3, "$", 2));

        BigDecimal dif1 = opt1.subtract(new BigDecimal(this.exchange1In.getText()));
        BigDecimal dif2 = opt2.subtract(new BigDecimal(this.exchange2In.getText()));
        BigDecimal dif3 = opt3.subtract(new BigDecimal(this.exchange3In.getText()));
        this.exchange1Diff.setText(formatBigDecimal(dif1, "$", 2));
        this.exchange2Diff.setText(formatBigDecimal(dif2, "$", 2));
        this.exchange3Diff.setText(formatBigDecimal(dif3, "$", 2));
        updateDashboard(false);
    }

    private void loadDashboardData() {
        FileInputStream fi = null;
        ObjectInputStream oi = null;
        try {
            fi = new FileInputStream(new File("dashboard.dat"));
            oi = new ObjectInputStream(fi);
            HashMap loaded = (HashMap) oi.readObject();
            this.holdingDistribution.setValue(loaded.get("holdingDistribution"));
            this.miningDistribution.setValue(loaded.get("miningDistribution"));
            this.tradingDistribution.setValue(loaded.get("tradingDistribution"));
            this.anitaInvestment.setValue(loaded.getOrDefault("anitaInvestment", 1L));
            this.woutInvestment.setValue(loaded.getOrDefault("woutInvestment", 1L));
        } catch (FileNotFoundException ex) {

        } catch (IOException ex) {

        } catch (ClassNotFoundException ex) {
            Logger.getLogger(MainScreen.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                if (!isNull(oi)) {
                    oi.close();
                }
                if (!isNull(fi)) {
                    fi.close();
                }
            } catch (IOException ex) {

            }
        }
        updateDashboard(true);
    }

    private void updateDashboard(boolean reloadChilds) {
        if (reloadChilds) {
            printPortofolio();
            updateMiningTotal();
            updateTrade();
        }

        BigDecimal totalUSD = haveHold.add(haveMine).add(haveTrade);
        BigDecimal totalEUR = totalUSD.divide(euro2Dollar, mc);
        BigDecimal totalBTC = totalUSD.divide(btc2Dollar, mc);
        this.totalDashboardDollar.setText(formatBigDecimal(totalUSD, "$", 2));
        this.totalDashboardEuro.setText(formatBigDecimal(totalEUR, "€", 2));
        this.totalDashboardBitcoin.setText(formatBigDecimal(totalBTC, "B", 8));

        BigDecimal totalInvestment = new BigDecimal((long) this.anitaInvestment.getValue());
        totalInvestment = totalInvestment.add(new BigDecimal((Long) this.woutInvestment.getValue()));
        BigDecimal anitaInvestmentMultiplier = (new BigDecimal((Long) this.anitaInvestment.getValue())).divide(totalInvestment, mc);
        BigDecimal woutInvestmentMultiplier = (new BigDecimal((Long) this.woutInvestment.getValue())).divide(totalInvestment, mc);

        this.anitaTotalDollar.setText(formatBigDecimal(totalUSD.multiply(anitaInvestmentMultiplier), "$", 2));
        this.woutTotalDollar.setText(formatBigDecimal(totalUSD.multiply(woutInvestmentMultiplier), "$", 2));
        this.anitaTotalEuro.setText(formatBigDecimal(totalEUR.multiply(anitaInvestmentMultiplier), "€", 2));
        this.woutTotalEuro.setText(formatBigDecimal(totalEUR.multiply(woutInvestmentMultiplier), "€", 2));
        this.anitaTotalBitcoin.setText(formatBigDecimal(totalBTC.multiply(anitaInvestmentMultiplier), "B", 8));
        this.woutTotalBitcoin.setText(formatBigDecimal(totalBTC.multiply(woutInvestmentMultiplier), "B", 8));

        BigDecimal totalDist = new BigDecimal((int) holdingDistribution.getValue() + (int) miningDistribution.getValue() + (int) tradingDistribution.getValue());
        if (totalDist.compareTo(BigDecimal.ZERO) == 0) {
            totalDist = new BigDecimal(1);
        }
        optimalHold = totalUSD.divide(totalDist, mc).multiply(new BigDecimal((int) holdingDistribution.getValue()), mc);
        optimalMine = totalUSD.divide(totalDist, mc).multiply(new BigDecimal((int) miningDistribution.getValue()), mc);
        optimalTrade = totalUSD.divide(totalDist, mc).multiply(new BigDecimal((int) tradingDistribution.getValue()), mc);
        this.holdingOptimal.setText(formatBigDecimal(optimalHold, "$", 2));
        this.miningOptimal.setText(formatBigDecimal(optimalMine, "$", 2));
        this.tradingOptimal.setText(formatBigDecimal(optimalTrade, "$", 2));

        diffHold = optimalHold.subtract(haveHold);
        diffMine = optimalMine.subtract(haveMine);
        diffTrade = optimalTrade.subtract(haveTrade);
        this.holdingDiff.setText(formatBigDecimal(diffHold, "$", 2));
        this.miningDiff.setText(formatBigDecimal(diffMine, "$", 2));
        this.tradingDiff.setText(formatBigDecimal(diffTrade, "$", 2));
        updateStats();
    }

    private void loadStatData() {
        FileInputStream fi = null;
        ObjectInputStream oi = null;
        try {
            fi = new FileInputStream(new File("stat.dat"));
            oi = new ObjectInputStream(fi);
            HashMap loaded = (HashMap) oi.readObject();
            earningsDateStart = (Date) loaded.get("earningsDateStart");
            earningsHoldStart = new BigDecimal(loaded.get("earningsHoldStart").toString());
            earningsMineStart = new BigDecimal(loaded.get("earningsMineStart").toString());
            earningsTradeStart = new BigDecimal(loaded.get("earningsTradeStart").toString());
            earningsExchange1Start = new BigDecimal(loaded.get("earningsExchange1Start").toString());
            earningsExchange2Start = new BigDecimal(loaded.get("earningsExchange2Start").toString());
            earningsExchange3Start = new BigDecimal(loaded.get("earningsExchange3Start").toString());
        } catch (FileNotFoundException ex) {

        } catch (IOException ex) {

        } catch (ClassNotFoundException ex) {
            Logger.getLogger(MainScreen.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                if (!isNull(oi)) {
                    oi.close();
                }
                if (!isNull(fi)) {
                    fi.close();
                }
            } catch (IOException ex) {

            }
        }
    }

    private void updateStats() {
        this.totalEarningsSince.setText("Earnings since: " + df.format(earningsDateStart));
        this.totalHoldingEarnings.setText(formatBigDecimal(haveHold.subtract(earningsHoldStart), "$", 2));
        this.totalMiningEarnings.setText(formatBigDecimal(haveMine.subtract(earningsMineStart), "$", 2));
        this.totalTradingEarnings.setText(formatBigDecimal(haveTrade.subtract(earningsTradeStart), "$", 2));
        BigDecimal totalStart = new BigDecimal(0).add(haveHold).add(haveMine).add(haveTrade).subtract(earningsHoldStart).subtract(earningsMineStart).subtract(earningsTradeStart);
        this.totalEarnings.setText(formatBigDecimal(totalStart, "$", 2));
        try {
            this.totalHoldingEarningsPercent.setText(formatBigDecimal(haveHold.divide(earningsHoldStart, mc).multiply(new BigDecimal(100)).subtract(new BigDecimal(100)), "%", 2));
        } catch (Exception e) {

        }
        try {
            this.totalMiningEarningsPercent.setText(formatBigDecimal(haveMine.divide(earningsMineStart, mc).multiply(new BigDecimal(100)).subtract(new BigDecimal(100)), "%", 2));
        } catch (Exception e) {

        }
        try {
            this.totalTradingEarningsPercent.setText(formatBigDecimal(haveTrade.divide(earningsTradeStart, mc).multiply(new BigDecimal(100)).subtract(new BigDecimal(100)), "%", 2));
        } catch (Exception e) {

        }
        try {
            this.totalEarningsPercent.setText(formatBigDecimal(haveHold.add(haveMine).add(haveTrade).divide(earningsHoldStart.add(earningsMineStart).add(earningsTradeStart), mc).multiply(new BigDecimal(100)).subtract(new BigDecimal(100)), "%", 2));
        } catch (Exception e) {

        }
        BigDecimal days = new BigDecimal(new Date().getTime()).subtract(new BigDecimal(earningsDateStart.getTime())).divide(new BigDecimal(86400000), mc);
        this.totalEarningsDays.setText(days.toPlainString() + " days");
        try {
            this.totalHoldingEarningsPerDay.setText(formatBigDecimal(haveHold.subtract(earningsHoldStart).divide(days, mc), "$", 2) + " /day");
        } catch (Exception e) {

        }
        try {
            this.totalMiningEarningsPerDay.setText(formatBigDecimal(haveMine.subtract(earningsMineStart).divide(days, mc), "$", 2) + " /day");
        } catch (Exception e) {

        }
        try {
            this.totalTradingEarningsPerDay.setText(formatBigDecimal(haveTrade.subtract(earningsTradeStart).divide(days, mc), "$", 2) + " /day");
        } catch (Exception e) {

        }
        try {
            this.totalEarningsPerDay.setText(formatBigDecimal(totalStart.divide(days, mc), "$", 2) + " /day");
        } catch (Exception e) {

        }
        this.tradeEarningsSince.setText("Earnings since: " + df.format(earningsDateStart));
        this.tradeExchange1Earnings.setText(formatBigDecimal(new BigDecimal(this.exchange1In.getText()).subtract(earningsExchange1Start), "$", 2));
        this.tradeExchange2Earnings.setText(formatBigDecimal(new BigDecimal(this.exchange2In.getText()).subtract(earningsExchange2Start), "$", 2));
        this.tradeExchange3Earnings.setText(formatBigDecimal(new BigDecimal(this.exchange3In.getText()).subtract(earningsExchange3Start), "$", 2));
        BigDecimal tradeStart = new BigDecimal(0).add(new BigDecimal(this.exchange1In.getText())).add(new BigDecimal(this.exchange2In.getText())).add(new BigDecimal(this.exchange3In.getText())).subtract(earningsExchange1Start).subtract(earningsExchange2Start).subtract(earningsExchange3Start);
        this.tradeEarnings.setText(formatBigDecimal(tradeStart, "$", 2));
        try {
            this.tradeExchange1EarningsPercent.setText(formatBigDecimal(new BigDecimal(this.exchange1In.getText()).divide(earningsExchange1Start, mc).multiply(new BigDecimal(100)).subtract(new BigDecimal(100)), "%", 2));
        } catch (Exception e) {

        }
        try {
            this.tradeExchange2EarningsPercent.setText(formatBigDecimal(new BigDecimal(this.exchange2In.getText()).divide(earningsExchange2Start, mc).multiply(new BigDecimal(100)).subtract(new BigDecimal(100)), "%", 2));
        } catch (Exception e) {

        }
        try {
            this.tradeExchange3EarningsPercent.setText(formatBigDecimal(new BigDecimal(this.exchange3In.getText()).divide(earningsExchange3Start, mc).multiply(new BigDecimal(100)).subtract(new BigDecimal(100)), "%", 2));
        } catch (Exception e) {

        }
        try {
            this.tradeEarningsPercent.setText(formatBigDecimal(new BigDecimal(this.exchange1In.getText()).add(new BigDecimal(this.exchange2In.getText())).add(new BigDecimal(this.exchange3In.getText())).divide(earningsExchange1Start.add(earningsExchange2Start).add(earningsExchange3Start), mc).multiply(new BigDecimal(100)).subtract(new BigDecimal(100)), "%", 2));
        } catch (Exception e) {
        }
        this.tradeEarningsDays.setText(days.toPlainString() + " days");
        try {
            this.tradeExchange1EarningsPerDay.setText(formatBigDecimal(new BigDecimal(this.exchange1In.getText()).subtract(earningsExchange1Start).divide(days, mc), "$", 2) + " /day");
        } catch (Exception e) {

        }
        try {
            this.tradeExchange2EarningsPerDay.setText(formatBigDecimal(new BigDecimal(this.exchange2In.getText()).subtract(earningsExchange2Start).divide(days, mc), "$", 2) + " /day");
        } catch (Exception e) {

        }
        try {
            this.tradeExchange3EarningsPerDay.setText(formatBigDecimal(new BigDecimal(this.exchange3In.getText()).subtract(earningsExchange3Start).divide(days, mc), "$", 2) + " /day");
        } catch (Exception e) {

        }
        try {
            this.tradeEarningsPerDay.setText(formatBigDecimal(tradeStart.divide(days, mc), "$", 2) + " /day");
        } catch (Exception e) {

        }
    }

    private boolean connectToExchange(int n) {
        boolean success = false;
        String apiKey = "";
        String apiSecret = "";
        JLabel statusLabel = this.exchange1Status;
        JTextField exchangeIn = this.exchange1In;
        switch (n) {
            case 1:
                apiKey = this.exchange1ApiKey.getText();
                apiSecret = this.exchange1ApiSecret.getText();
                break;
            case 2:
                apiKey = this.exchange2ApiKey.getText();
                apiSecret = this.exchange2ApiSecret.getText();
                statusLabel = this.exchange2Status;
                exchangeIn = this.exchange2In;
                break;
            case 3:
                apiKey = this.exchange3ApiKey.getText();
                apiSecret = this.exchange3ApiSecret.getText();
                statusLabel = this.exchange3Status;
                exchangeIn = this.exchange3In;
                break;
        }
        if (!apiKey.equals("") && !apiSecret.equals("")) {
            try {
                TradeApi ta = new TradeApi(apiKey, apiSecret);
                ta.getInfo.runMethod();
                if (ta.getInfo.isSuccess()) {
                    statusLabel.setText("Connected!");
                    log4Api("The connection to WEX #" + n + " was successful.");
                    ta.info.runMethod();
                    ArrayList<String> temp = ta.info.getPairsList();
                    ArrayList<String> validPairs = new ArrayList<>();
                    for (int i = 0; i < temp.size(); i++) {
                        if (!validPairs.contains(temp.get(i).split("-")[0])) {
                            validPairs.add(temp.get(i).split("-")[0]);
                        }
                        if (!validPairs.contains(temp.get(i).split("-")[1])) {
                            validPairs.add(temp.get(i).split("-")[1]);
                        }
                    }
                    if (ta.info.isSuccess()) {
                        BigDecimal balance = new BigDecimal(ta.getInfo.getBalance("usd"));
                        balance = balance.add(new BigDecimal(ta.getInfo.getBalance("eur")).multiply(euro2Dollar, mc));
                        for (int i = 0; i < cap_original.size(); i++) {
                            if (validPairs.contains(cap_original.get(i).get("symbol").toString())) {
                                balance = balance.add(new BigDecimal(ta.getInfo.getBalance(cap_original.get(i).get("symbol").toString().toLowerCase())).multiply(new BigDecimal(cap_original.get(i).get("price_usd").toString()), mc));
                            } else //Exceptions
                            if (cap_original.get(i).get("symbol").toString().equals("DASH")) {
                                balance = balance.add(new BigDecimal(ta.getInfo.getBalance("dsh")).multiply(new BigDecimal(cap_original.get(i).get("price_usd").toString()), mc));
                            }
                        }
                        exchangeIn.setText(balance.toPlainString());
                        success = true;
                    } else {
                        statusLabel.setText("Error");
                        log4Api("An error occured while connecting to WEX #" + n + ". Though the connection was successful, the balance could not be retrieved.");
                        log4Api("Details: " + ta.info.getErrorMessage());
                    }
                } else {
                    statusLabel.setText("Error");
                    log4Api("An error occured while connecting to WEX #" + n + ". This might be due to invalid API keys.");
                    log4Api("Details: " + ta.getInfo.getErrorMessage());
                }
            } catch (Exception e) {
                statusLabel.setText("Error");
                log4Api("An error occured while connecting to WEX #" + n + ". This might be due to a network error.");
                log4Api("Details: " + e.getLocalizedMessage());
            }
        } else if (!startup) {
            statusLabel.setText("Not connected");
            log4Api("Error: The keys and secrets for WEX #" + n + " are empty.");
        }
        return success;
    }

    private String formatBigDecimal(BigDecimal bd, String symbol, int digits) {
        String s = symbol + bd.toPlainString();
        if ((s.indexOf(".") + (digits + 1)) < s.length()) {
            s = s.substring(0, s.indexOf(".") + digits + 1);
        }
        return s;
    }

    private void log4Api(String message) {
        if (!this.tradeLogBox.getText().equals("")) {
            this.tradeLogBox.setText(this.tradeLogBox.getText() + System.lineSeparator());
        }
        this.tradeLogBox.setText(this.tradeLogBox.getText() + message);
        this.tradeLogBox.setCaretPosition(0);
    }

    private void changeCurrency() {
        BigDecimal total = new BigDecimal(this.totalDashboardDollar.getText().substring(1));
        BigDecimal anita = new BigDecimal(this.anitaTotalDollar.getText().substring(1));
        BigDecimal wout = new BigDecimal(this.woutTotalDollar.getText().substring(1));
        String symbol = "";
        if (currentCurrency.equals("eur")) {
            total = total.divide(ars2Dollar, mc);
            anita = anita.divide(ars2Dollar, mc);
            wout = wout.divide(ars2Dollar, mc);
            symbol = "$";
            currentCurrency = "ars";
            this.euroDollarExchange.setText("AR/$: " + BigDecimal.ONE.divide(ars2Dollar, mc).toPlainString());
        } else {
            total = total.divide(euro2Dollar, mc);
            anita = anita.divide(euro2Dollar, mc);
            wout = wout.divide(euro2Dollar, mc);
            symbol = "€";
            currentCurrency = "eur";
            this.euroDollarExchange.setText("$/€: " + euro2Dollar.toPlainString());
        }
        this.totalDashboardEuro.setText(formatBigDecimal(total, symbol, 2));
        this.anitaTotalEuro.setText(formatBigDecimal(anita, symbol, 2));
        this.woutTotalEuro.setText(formatBigDecimal(wout, symbol, 2));
    }

    private void changeCurrencyConv() {
        currentConv = ((currentConv + 1) % 3) + 1;
        if (currentConv == 1) {
            this.euroDollarExchange.setText("$/€: " + euro2Dollar.toPlainString());
        }
        if (currentConv == 3) {
            this.euroDollarExchange.setText("AR/$: " + BigDecimal.ONE.divide(ars2Dollar, mc).toPlainString());
        }
        if (currentConv == 2) {
            this.euroDollarExchange.setText("AR/€: " + BigDecimal.ONE.divide(ars2Dollar, mc).multiply(euro2Dollar, mc).toPlainString());
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jTabbedPane1 = new javax.swing.JTabbedPane();
        jPanel = new javax.swing.JPanel();
        jPanel5 = new javax.swing.JPanel();
        jLabel5 = new javax.swing.JLabel();
        holdingDistribution = new javax.swing.JSpinner();
        miningDistribution = new javax.swing.JSpinner();
        jLabel6 = new javax.swing.JLabel();
        tradingDistribution = new javax.swing.JSpinner();
        jLabel7 = new javax.swing.JLabel();
        jPanel9 = new javax.swing.JPanel();
        jLabel23 = new javax.swing.JLabel();
        jLabel24 = new javax.swing.JLabel();
        jLabel25 = new javax.swing.JLabel();
        jLabel28 = new javax.swing.JLabel();
        jLabel42 = new javax.swing.JLabel();
        jLabel30 = new javax.swing.JLabel();
        holdingOptimal = new javax.swing.JLabel();
        tradingHave = new javax.swing.JLabel();
        miningHave = new javax.swing.JLabel();
        holdingHave = new javax.swing.JLabel();
        miningOptimal = new javax.swing.JLabel();
        tradingOptimal = new javax.swing.JLabel();
        jLabel37 = new javax.swing.JLabel();
        holdingDiff = new javax.swing.JLabel();
        miningDiff = new javax.swing.JLabel();
        tradingDiff = new javax.swing.JLabel();
        jLabel41 = new javax.swing.JLabel();
        totalDashboardDollar = new javax.swing.JLabel();
        totalDashboardEuro = new javax.swing.JLabel();
        jLabel55 = new javax.swing.JLabel();
        jLabel56 = new javax.swing.JLabel();
        anitaTotalDollar = new javax.swing.JLabel();
        woutTotalDollar = new javax.swing.JLabel();
        anitaTotalEuro = new javax.swing.JLabel();
        woutTotalEuro = new javax.swing.JLabel();
        totalDashboardBitcoin = new javax.swing.JLabel();
        anitaTotalBitcoin = new javax.swing.JLabel();
        woutTotalBitcoin = new javax.swing.JLabel();
        jPanel10 = new javax.swing.JPanel();
        totalEarningsSince = new javax.swing.JLabel();
        jLabel32 = new javax.swing.JLabel();
        jLabel33 = new javax.swing.JLabel();
        jLabel34 = new javax.swing.JLabel();
        jLabel35 = new javax.swing.JLabel();
        totalTradingEarnings = new javax.swing.JLabel();
        totalMiningEarnings = new javax.swing.JLabel();
        totalHoldingEarnings = new javax.swing.JLabel();
        totalEarnings = new javax.swing.JLabel();
        totalHoldingEarningsPercent = new javax.swing.JLabel();
        totalMiningEarningsPercent = new javax.swing.JLabel();
        totalTradingEarningsPercent = new javax.swing.JLabel();
        totalEarningsPercent = new javax.swing.JLabel();
        totalEarningsDays = new javax.swing.JLabel();
        totalHoldingEarningsPerDay = new javax.swing.JLabel();
        totalMiningEarningsPerDay = new javax.swing.JLabel();
        totalTradingEarningsPerDay = new javax.swing.JLabel();
        totalEarningsPerDay = new javax.swing.JLabel();
        jLabel46 = new javax.swing.JLabel();
        anitaEarnings = new javax.swing.JLabel();
        anitaEarningsPercent = new javax.swing.JLabel();
        anitaEarningsPerDay = new javax.swing.JLabel();
        jLabel51 = new javax.swing.JLabel();
        woutEarnings = new javax.swing.JLabel();
        woutEarningsPercent = new javax.swing.JLabel();
        woutEarningsPerDay = new javax.swing.JLabel();
        euroDollarExchange = new javax.swing.JLabel();
        jPanel11 = new javax.swing.JPanel();
        jLabel43 = new javax.swing.JLabel();
        earningTypeToModify = new javax.swing.JComboBox<>();
        jLabel44 = new javax.swing.JLabel();
        earningAmountToModify = new javax.swing.JTextField();
        modifyEarningsButton = new javax.swing.JButton();
        resetAllEarningsButton = new javax.swing.JButton();
        jLabel45 = new javax.swing.JLabel();
        jPanel14 = new javax.swing.JPanel();
        jLabel52 = new javax.swing.JLabel();
        anitaInvestment = new javax.swing.JSpinner();
        woutInvestment = new javax.swing.JSpinner();
        jLabel54 = new javax.swing.JLabel();
        jPanel2 = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        coinMarketCapsArea = new javax.swing.JTextArea();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jScrollPane2 = new javax.swing.JScrollPane();
        coinPortefolioArea = new javax.swing.JTextArea();
        coinComboBox = new javax.swing.JComboBox<>();
        coinQuantity = new javax.swing.JTextField();
        addCoinButton = new javax.swing.JButton();
        enterPercentage = new javax.swing.JTextField();
        jLabel57 = new javax.swing.JLabel();
        exitPercentage = new javax.swing.JTextField();
        jLabel59 = new javax.swing.JLabel();
        jLabel58 = new javax.swing.JLabel();
        filterPortofolioButton = new javax.swing.JButton();
        jPanel3 = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        jScrollPane4 = new javax.swing.JScrollPane();
        contractDataTable = new javax.swing.JTable();
        totalInMining = new javax.swing.JLabel();
        jPanel6 = new javax.swing.JPanel();
        jLabel4 = new javax.swing.JLabel();
        jLabel8 = new javax.swing.JLabel();
        contractPrice = new javax.swing.JTextField();
        jLabel9 = new javax.swing.JLabel();
        contractTypeComboBox = new javax.swing.JComboBox<>();
        jLabel10 = new javax.swing.JLabel();
        contractDays = new javax.swing.JTextField();
        jLabel11 = new javax.swing.JLabel();
        jLabel12 = new javax.swing.JLabel();
        contractBitcoinPrice = new javax.swing.JTextField();
        addContractFeeButton = new javax.swing.JButton();
        viewContractFeesButton = new javax.swing.JButton();
        contractStartDate = new javax.swing.JTextField();
        jLabel13 = new javax.swing.JLabel();
        jLabel38 = new javax.swing.JLabel();
        miningPayoutWas = new javax.swing.JLabel();
        jScrollPane3 = new javax.swing.JScrollPane();
        miningBuyList = new javax.swing.JList<>();
        jLabel14 = new javax.swing.JLabel();
        miningWalletUSD = new javax.swing.JTextField();
        jLabel31 = new javax.swing.JLabel();
        miningWalletBTC = new javax.swing.JTextField();
        jPanel4 = new javax.swing.JPanel();
        jPanel7 = new javax.swing.JPanel();
        jLabel18 = new javax.swing.JLabel();
        jLabel19 = new javax.swing.JLabel();
        jLabel20 = new javax.swing.JLabel();
        jLabel21 = new javax.swing.JLabel();
        exchange3In = new javax.swing.JTextField();
        exchange2In = new javax.swing.JTextField();
        exchange1In = new javax.swing.JTextField();
        jLabel22 = new javax.swing.JLabel();
        exchange1Opt = new javax.swing.JLabel();
        exchange2Opt = new javax.swing.JLabel();
        exchange3Opt = new javax.swing.JLabel();
        jLabel26 = new javax.swing.JLabel();
        jLabel27 = new javax.swing.JLabel();
        exchange1Diff = new javax.swing.JLabel();
        exchange2Diff = new javax.swing.JLabel();
        exchange3Diff = new javax.swing.JLabel();
        jLabel29 = new javax.swing.JLabel();
        totalTrading = new javax.swing.JLabel();
        jPanel8 = new javax.swing.JPanel();
        jLabel15 = new javax.swing.JLabel();
        jLabel16 = new javax.swing.JLabel();
        jLabel17 = new javax.swing.JLabel();
        exchange3Dist = new javax.swing.JSpinner();
        exchange2Dist = new javax.swing.JSpinner();
        exchange1Dist = new javax.swing.JSpinner();
        jPanel12 = new javax.swing.JPanel();
        tradeEarningsSince = new javax.swing.JLabel();
        jLabel47 = new javax.swing.JLabel();
        jLabel48 = new javax.swing.JLabel();
        jLabel49 = new javax.swing.JLabel();
        jLabel50 = new javax.swing.JLabel();
        tradeExchange3Earnings = new javax.swing.JLabel();
        tradeExchange1Earnings = new javax.swing.JLabel();
        tradeExchange2Earnings = new javax.swing.JLabel();
        tradeEarnings = new javax.swing.JLabel();
        tradeExchange1EarningsPercent = new javax.swing.JLabel();
        tradeExchange2EarningsPercent = new javax.swing.JLabel();
        tradeExchange3EarningsPercent = new javax.swing.JLabel();
        tradeEarningsPercent = new javax.swing.JLabel();
        tradeEarningsDays = new javax.swing.JLabel();
        tradeExchange1EarningsPerDay = new javax.swing.JLabel();
        tradeExchange2EarningsPerDay = new javax.swing.JLabel();
        tradeExchange3EarningsPerDay = new javax.swing.JLabel();
        tradeEarningsPerDay = new javax.swing.JLabel();
        jPanel13 = new javax.swing.JPanel();
        jLabel36 = new javax.swing.JLabel();
        exchange1ApiKey = new javax.swing.JTextField();
        exchange1ConnectButton = new javax.swing.JButton();
        exchange1ApiSecret = new javax.swing.JTextField();
        jLabel39 = new javax.swing.JLabel();
        jLabel40 = new javax.swing.JLabel();
        exchange3ApiKey = new javax.swing.JTextField();
        exchange3ApiSecret = new javax.swing.JTextField();
        exchange2ApiKey = new javax.swing.JTextField();
        exchange2ApiSecret = new javax.swing.JTextField();
        exchange2ConnectButton = new javax.swing.JButton();
        exchange1Status = new javax.swing.JLabel();
        exchange2Status = new javax.swing.JLabel();
        exchange3Status = new javax.swing.JLabel();
        exchange3ConnectButton = new javax.swing.JButton();
        jLabel53 = new javax.swing.JLabel();
        jScrollPane5 = new javax.swing.JScrollPane();
        tradeLogBox = new javax.swing.JTextArea();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Crypto Finances");
        setResizable(false);

        jPanel5.setBorder(javax.swing.BorderFactory.createTitledBorder("Money distribution"));

        jLabel5.setText("Holding");

        holdingDistribution.setModel(new javax.swing.SpinnerNumberModel(1, 0, null, 1));
        holdingDistribution.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                holdingDistributionStateChanged(evt);
            }
        });

        miningDistribution.setModel(new javax.swing.SpinnerNumberModel(1, 0, null, 1));
        miningDistribution.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                miningDistributionStateChanged(evt);
            }
        });

        jLabel6.setText("Mining");

        tradingDistribution.setModel(new javax.swing.SpinnerNumberModel(1, 0, null, 1));
        tradingDistribution.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                tradingDistributionStateChanged(evt);
            }
        });

        jLabel7.setText("Trading");

        javax.swing.GroupLayout jPanel5Layout = new javax.swing.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel5)
                    .addComponent(jLabel6)
                    .addComponent(jLabel7))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(miningDistribution)
                    .addComponent(holdingDistribution, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 74, Short.MAX_VALUE)
                    .addComponent(tradingDistribution))
                .addContainerGap())
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel5)
                    .addComponent(holdingDistribution, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(miningDistribution, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel6))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(tradingDistribution, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel7))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel9.setBorder(javax.swing.BorderFactory.createTitledBorder("Overview"));
        jPanel9.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jPanel9MouseClicked(evt);
            }
        });

        jLabel23.setText("USD/EUR");

        jLabel24.setText("Holding");

        jLabel25.setText("Mining");

        jLabel28.setText("Trading");

        jLabel42.setText("Total");

        jLabel30.setText("Amount you have");

        holdingOptimal.setText("$0.00");

        tradingHave.setText("$0.00");

        miningHave.setText("$0.00");

        holdingHave.setText("$0.00");

        miningOptimal.setText("$0.00");

        tradingOptimal.setText("$0.00");

        jLabel37.setText("Optimal amount");

        holdingDiff.setText("$0.00");

        miningDiff.setText("$0.00");

        tradingDiff.setText("$0.00");

        jLabel41.setText("Difference");

        totalDashboardDollar.setText("$0.00");

        totalDashboardEuro.setText("€0.00");

        jLabel55.setText("Anita");

        jLabel56.setText("Wout");

        anitaTotalDollar.setText("$0.00");

        woutTotalDollar.setText("$0.00");

        anitaTotalEuro.setText("€0.00");

        woutTotalEuro.setText("€0.00");

        totalDashboardBitcoin.setText("0.00000000");

        anitaTotalBitcoin.setText("0.00000000");

        woutTotalBitcoin.setText("0.00000000");

        javax.swing.GroupLayout jPanel9Layout = new javax.swing.GroupLayout(jPanel9);
        jPanel9.setLayout(jPanel9Layout);
        jPanel9Layout.setHorizontalGroup(
            jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel9Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel9Layout.createSequentialGroup()
                        .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanel9Layout.createSequentialGroup()
                                .addComponent(jLabel23)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(jLabel30))
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanel9Layout.createSequentialGroup()
                                .addComponent(jLabel24)
                                .addGap(18, 18, 18)
                                .addComponent(holdingHave, javax.swing.GroupLayout.PREFERRED_SIZE, 124, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanel9Layout.createSequentialGroup()
                                .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(jLabel28)
                                    .addComponent(jLabel25)
                                    .addComponent(jLabel42))
                                .addGap(18, 18, 18)
                                .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(miningHave, javax.swing.GroupLayout.DEFAULT_SIZE, 124, Short.MAX_VALUE)
                                    .addComponent(tradingHave, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(totalDashboardDollar, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel9Layout.createSequentialGroup()
                                .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addGroup(jPanel9Layout.createSequentialGroup()
                                        .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                            .addComponent(tradingOptimal, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 124, Short.MAX_VALUE)
                                            .addComponent(miningOptimal, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addComponent(miningDiff, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                            .addComponent(tradingDiff, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                                    .addGroup(jPanel9Layout.createSequentialGroup()
                                        .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addComponent(holdingOptimal, javax.swing.GroupLayout.PREFERRED_SIZE, 124, javax.swing.GroupLayout.PREFERRED_SIZE)
                                            .addComponent(jLabel37))
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addComponent(jLabel41)
                                            .addComponent(holdingDiff, javax.swing.GroupLayout.PREFERRED_SIZE, 124, javax.swing.GroupLayout.PREFERRED_SIZE))))
                                .addGap(0, 0, Short.MAX_VALUE))
                            .addGroup(jPanel9Layout.createSequentialGroup()
                                .addComponent(totalDashboardEuro, javax.swing.GroupLayout.PREFERRED_SIZE, 125, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(10, 10, 10)
                                .addComponent(totalDashboardBitcoin, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                    .addGroup(jPanel9Layout.createSequentialGroup()
                        .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel56)
                            .addComponent(jLabel55))
                        .addGap(29, 29, 29)
                        .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(anitaTotalDollar, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(woutTotalDollar, javax.swing.GroupLayout.PREFERRED_SIZE, 123, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel9Layout.createSequentialGroup()
                                .addComponent(woutTotalEuro, javax.swing.GroupLayout.PREFERRED_SIZE, 123, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(woutTotalBitcoin, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                            .addGroup(jPanel9Layout.createSequentialGroup()
                                .addComponent(anitaTotalEuro, javax.swing.GroupLayout.PREFERRED_SIZE, 123, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(anitaTotalBitcoin, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))))
                .addContainerGap())
        );
        jPanel9Layout.setVerticalGroup(
            jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel9Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel23)
                    .addComponent(jLabel30)
                    .addComponent(jLabel37)
                    .addComponent(jLabel41))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel24)
                    .addComponent(holdingOptimal)
                    .addComponent(holdingHave)
                    .addComponent(holdingDiff))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(jLabel25)
                        .addComponent(miningHave))
                    .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(miningOptimal, javax.swing.GroupLayout.PREFERRED_SIZE, 15, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(miningDiff)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel28)
                    .addComponent(tradingHave)
                    .addComponent(tradingOptimal)
                    .addComponent(tradingDiff))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel42)
                    .addComponent(totalDashboardDollar)
                    .addComponent(totalDashboardEuro)
                    .addComponent(totalDashboardBitcoin))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel55)
                    .addComponent(anitaTotalDollar)
                    .addComponent(anitaTotalEuro)
                    .addComponent(anitaTotalBitcoin))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel56)
                    .addComponent(woutTotalDollar)
                    .addComponent(woutTotalEuro)
                    .addComponent(woutTotalBitcoin))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel10.setBorder(javax.swing.BorderFactory.createTitledBorder("Total earnings"));

        totalEarningsSince.setText("Earnings since: 01/01/2009");

        jLabel32.setText("Holding");

        jLabel33.setText("Mining");

        jLabel34.setText("Trading");

        jLabel35.setText("Total");

        totalTradingEarnings.setText("$0.00");

        totalMiningEarnings.setText("$0.00");

        totalHoldingEarnings.setText("$0.00");

        totalEarnings.setText("$0.00");

        totalHoldingEarningsPercent.setText("%0.00");

        totalMiningEarningsPercent.setText("%0.00");

        totalTradingEarningsPercent.setText("%0.00");

        totalEarningsPercent.setText("%0.00");

        totalEarningsDays.setText("0 days");

        totalHoldingEarningsPerDay.setText("$0.00 /day");

        totalMiningEarningsPerDay.setText("$0.00 /day");

        totalTradingEarningsPerDay.setText("$0.00 /day");

        totalEarningsPerDay.setText("$0.00 /day");

        jLabel46.setText("Anita");

        anitaEarnings.setText("$0.00");

        anitaEarningsPercent.setText("%0.00");

        anitaEarningsPerDay.setText("$0.00 /day");

        jLabel51.setText("Wout");

        woutEarnings.setText("$0.00");

        woutEarningsPercent.setText("%0.00");

        woutEarningsPerDay.setText("$0.00 /day");

        euroDollarExchange.setText("$1.00");
        euroDollarExchange.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                euroDollarExchangeMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout jPanel10Layout = new javax.swing.GroupLayout(jPanel10);
        jPanel10.setLayout(jPanel10Layout);
        jPanel10Layout.setHorizontalGroup(
            jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel10Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel10Layout.createSequentialGroup()
                        .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addGroup(jPanel10Layout.createSequentialGroup()
                                .addComponent(jLabel32)
                                .addGap(18, 18, 18)
                                .addComponent(totalHoldingEarnings, javax.swing.GroupLayout.PREFERRED_SIZE, 124, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(jPanel10Layout.createSequentialGroup()
                                .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(jLabel34)
                                    .addComponent(jLabel33)
                                    .addComponent(jLabel35))
                                .addGap(18, 18, 18)
                                .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(totalMiningEarnings, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(totalTradingEarnings, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(totalEarnings, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                            .addComponent(totalEarningsSince, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(totalHoldingEarningsPercent, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(totalMiningEarningsPercent, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(totalTradingEarningsPercent, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(totalEarningsPercent, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(totalEarningsDays, javax.swing.GroupLayout.DEFAULT_SIZE, 124, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 12, Short.MAX_VALUE)
                        .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                .addComponent(totalHoldingEarningsPerDay, javax.swing.GroupLayout.DEFAULT_SIZE, 124, Short.MAX_VALUE)
                                .addComponent(totalMiningEarningsPerDay, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(totalTradingEarningsPerDay, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(totalEarningsPerDay, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                            .addComponent(euroDollarExchange, javax.swing.GroupLayout.PREFERRED_SIZE, 136, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(jPanel10Layout.createSequentialGroup()
                        .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel51)
                            .addComponent(jLabel46))
                        .addGap(29, 29, 29)
                        .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(anitaEarnings, javax.swing.GroupLayout.DEFAULT_SIZE, 123, Short.MAX_VALUE)
                            .addComponent(woutEarnings, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(anitaEarningsPercent, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(woutEarningsPercent, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(woutEarningsPerDay, javax.swing.GroupLayout.DEFAULT_SIZE, 136, Short.MAX_VALUE)
                            .addComponent(anitaEarningsPerDay, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                .addContainerGap())
        );
        jPanel10Layout.setVerticalGroup(
            jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel10Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(totalEarningsSince)
                    .addComponent(totalEarningsDays)
                    .addComponent(euroDollarExchange, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel32)
                    .addComponent(totalHoldingEarnings)
                    .addComponent(totalHoldingEarningsPercent)
                    .addComponent(totalHoldingEarningsPerDay))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel33)
                    .addComponent(totalMiningEarnings)
                    .addComponent(totalMiningEarningsPercent)
                    .addComponent(totalMiningEarningsPerDay))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel34)
                    .addComponent(totalTradingEarnings)
                    .addComponent(totalTradingEarningsPercent)
                    .addComponent(totalTradingEarningsPerDay))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel35)
                    .addComponent(totalEarnings)
                    .addComponent(totalEarningsPercent)
                    .addComponent(totalEarningsPerDay))
                .addGap(18, 18, 18)
                .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel46)
                    .addComponent(anitaEarnings)
                    .addComponent(anitaEarningsPercent)
                    .addComponent(anitaEarningsPerDay))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel51)
                    .addComponent(woutEarnings)
                    .addComponent(woutEarningsPercent)
                    .addComponent(woutEarningsPerDay))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel11.setBorder(javax.swing.BorderFactory.createTitledBorder("Earnings settings"));

        jLabel43.setText("Increase initial money for:");

        earningTypeToModify.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Holding", "Mining", "Trading" }));

        jLabel44.setText("$");

        earningAmountToModify.setText("0.00");

        modifyEarningsButton.setText("Modify");
        modifyEarningsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                modifyEarningsButtonActionPerformed(evt);
            }
        });

        resetAllEarningsButton.setText("Reset all earnings");
        resetAllEarningsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetAllEarningsButtonActionPerformed(evt);
            }
        });

        jLabel45.setText("(Useful incase external money was added)");

        javax.swing.GroupLayout jPanel11Layout = new javax.swing.GroupLayout(jPanel11);
        jPanel11.setLayout(jPanel11Layout);
        jPanel11Layout.setHorizontalGroup(
            jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel11Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel11Layout.createSequentialGroup()
                        .addComponent(earningTypeToModify, javax.swing.GroupLayout.PREFERRED_SIZE, 120, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel44)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(earningAmountToModify))
                    .addGroup(jPanel11Layout.createSequentialGroup()
                        .addGroup(jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel43)
                            .addComponent(jLabel45))
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel11Layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addGroup(jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(modifyEarningsButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 94, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(resetAllEarningsButton, javax.swing.GroupLayout.Alignment.TRAILING))))
                .addContainerGap())
        );
        jPanel11Layout.setVerticalGroup(
            jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel11Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel43)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(earningTypeToModify, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel44)
                    .addComponent(earningAmountToModify, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(modifyEarningsButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabel45)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(resetAllEarningsButton)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel14.setBorder(javax.swing.BorderFactory.createTitledBorder(" Investment distribution"));

        jLabel52.setText("Anita");

        anitaInvestment.setModel(new javax.swing.SpinnerNumberModel(1L, 0L, null, 1L));
        anitaInvestment.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                anitaInvestmentStateChanged(evt);
            }
        });

        woutInvestment.setModel(new javax.swing.SpinnerNumberModel(1L, 0L, null, 1L));
        woutInvestment.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                woutInvestmentStateChanged(evt);
            }
        });

        jLabel54.setText("Wout");

        javax.swing.GroupLayout jPanel14Layout = new javax.swing.GroupLayout(jPanel14);
        jPanel14.setLayout(jPanel14Layout);
        jPanel14Layout.setHorizontalGroup(
            jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel14Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel52, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel54, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(woutInvestment)
                    .addComponent(anitaInvestment, javax.swing.GroupLayout.DEFAULT_SIZE, 74, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel14Layout.setVerticalGroup(
            jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel14Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel52)
                    .addComponent(anitaInvestment, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(woutInvestment, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel54))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout jPanelLayout = new javax.swing.GroupLayout(jPanel);
        jPanel.setLayout(jPanelLayout);
        jPanelLayout.setHorizontalGroup(
            jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jPanel9, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(jPanelLayout.createSequentialGroup()
                        .addGroup(jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(jPanel5, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jPanel14, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jPanel11, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel10, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(264, Short.MAX_VALUE))
        );
        jPanelLayout.setVerticalGroup(
            jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanelLayout.createSequentialGroup()
                        .addComponent(jPanel10, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 213, Short.MAX_VALUE))
                    .addGroup(jPanelLayout.createSequentialGroup()
                        .addComponent(jPanel9, javax.swing.GroupLayout.PREFERRED_SIZE, 220, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanelLayout.createSequentialGroup()
                                .addComponent(jPanel5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jPanel14, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(jPanel11, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addContainerGap())
        );

        jTabbedPane1.addTab("Dashboard", jPanel);

        coinMarketCapsArea.setEditable(false);
        coinMarketCapsArea.setBackground(new java.awt.Color(238, 238, 238));
        coinMarketCapsArea.setColumns(20);
        coinMarketCapsArea.setRows(5);
        jScrollPane1.setViewportView(coinMarketCapsArea);

        jLabel1.setText("Coin market caps:");

        jLabel2.setText("Coins portefolio:");

        coinPortefolioArea.setEditable(false);
        coinPortefolioArea.setColumns(20);
        coinPortefolioArea.setRows(5);
        jScrollPane2.setViewportView(coinPortefolioArea);

        coinQuantity.setText("0.00000000");

        addCoinButton.setText("Add");
        addCoinButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addCoinButtonActionPerformed(evt);
            }
        });

        enterPercentage.setText("2.00");

        jLabel57.setText("Thresolhold enter:");

        exitPercentage.setText("0.50");

        jLabel59.setText("%");

        jLabel58.setText("%   Threshold exit:");

        filterPortofolioButton.setText("Save");
        filterPortofolioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                filterPortofolioButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jLabel2)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanel2Layout.createSequentialGroup()
                                .addComponent(coinComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 268, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(coinQuantity))
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanel2Layout.createSequentialGroup()
                                .addComponent(jLabel57)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(enterPercentage, javax.swing.GroupLayout.PREFERRED_SIZE, 98, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jLabel58)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(exitPercentage, javax.swing.GroupLayout.PREFERRED_SIZE, 98, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jLabel59)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(addCoinButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(filterPortofolioButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 568, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(jLabel1)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 657, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(jLabel2))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 360, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(addCoinButton)
                            .addComponent(coinQuantity, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(coinComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(enterPercentage, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel57)
                            .addComponent(exitPercentage, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel59)
                            .addComponent(jLabel58)
                            .addComponent(filterPortofolioButton))
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(jScrollPane1))
                .addContainerGap())
        );

        jTabbedPane1.addTab("Holding", jPanel2);

        jLabel3.setText("Mining contracts:");

        contractDataTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {"SHA256 (TH/s)", "30.00", "0.2", "0", "0.00000000", "0.00", "0", "0.00000000", "BTC", null,  new Boolean(true)},
                {"Scrypt (MH/s)", "28.00", "2", "730", "0.00000000", "0.00", "0", "0.00000000", "LTC", null,  new Boolean(true)},
                {"X11 (MH/s)", "30.00", "5", "730", "0.00000000", "0.00", "0", "0.00000000", "DASH", null,  new Boolean(true)},
                {"Dagger-Hashimoto (MH/s)", "29.99", "1", "730", "0.00000000", "0.00", "0", "0.00000000", "ETH", null,  new Boolean(true)},
                {"Equihash (H/s)", "47.99", "25", "730", "0.00000000", "0.00", "0", "0.00000000", "ZEC", null,  new Boolean(true)},
                {"Cryptonight (H/s)", "49.99", "60", "730", "0.00000000", "0.00", "0", "0.00000000", "XMR", null,  new Boolean(true)}
            },
            new String [] {
                "Contract", "Price/Contract", "Hashpower/Contract", "Days", "Additional fees", "Money in contract", "Your Hashpower", "Last payout", "Currency", "True payout (BTC)", "Available"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.Boolean.class
            };
            boolean[] canEdit = new boolean [] {
                false, true, true, true, false, true, true, true, true, false, true
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        contractDataTable.setColumnSelectionAllowed(true);
        contractDataTable.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                contractDataTablePropertyChange(evt);
            }
        });
        jScrollPane4.setViewportView(contractDataTable);
        contractDataTable.getColumnModel().getSelectionModel().setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);

        totalInMining.setText("Total in mining: $0.00");

        jPanel6.setBorder(javax.swing.BorderFactory.createTitledBorder("Fee calculation"));

        jLabel4.setText("Add Bitcoin price at contract time for fee calculation");

        jLabel8.setText("Contact price");

        jLabel9.setText("Contract type");

        contractTypeComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                contractTypeComboBoxActionPerformed(evt);
            }
        });

        jLabel10.setText("USD");

        jLabel11.setText("Days");

        jLabel12.setText("Bitcoin price");

        addContractFeeButton.setText("Add");
        addContractFeeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addContractFeeButtonActionPerformed(evt);
            }
        });

        viewContractFeesButton.setText("View all");
        viewContractFeesButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewContractFeesButtonActionPerformed(evt);
            }
        });

        jLabel13.setText("Start date");

        jLabel38.setText("(730 days ago)");

        javax.swing.GroupLayout jPanel6Layout = new javax.swing.GroupLayout(jPanel6);
        jPanel6.setLayout(jPanel6Layout);
        jPanel6Layout.setHorizontalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel4)
                    .addGroup(jPanel6Layout.createSequentialGroup()
                        .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel9)
                            .addComponent(jLabel8)
                            .addComponent(jLabel11)
                            .addComponent(jLabel12)
                            .addComponent(jLabel13))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(contractStartDate)
                            .addComponent(contractDays)
                            .addComponent(contractTypeComboBox, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(contractPrice)
                            .addComponent(contractBitcoinPrice)
                            .addComponent(addContractFeeButton, javax.swing.GroupLayout.DEFAULT_SIZE, 120, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel10)
                            .addComponent(viewContractFeesButton, javax.swing.GroupLayout.PREFERRED_SIZE, 120, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel38))))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel6Layout.setVerticalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addComponent(jLabel4)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel9)
                    .addComponent(contractTypeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(contractPrice, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel8)
                    .addComponent(jLabel10))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(contractDays, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel11))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel12)
                    .addComponent(contractBitcoinPrice, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel38))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(contractStartDate, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel13))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 88, Short.MAX_VALUE)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(addContractFeeButton)
                    .addComponent(viewContractFeesButton))
                .addContainerGap())
        );

        miningPayoutWas.setText("Complete payout was: $0.00");

        jScrollPane3.setViewportView(miningBuyList);

        jLabel14.setText("Amount in mining wallet: ($)");

        miningWalletUSD.setText("0.00");
        miningWalletUSD.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                miningWalletUSDActionPerformed(evt);
            }
        });

        jLabel31.setText("<=>  (BTC)");

        miningWalletBTC.setText("0.00000000");
        miningWalletBTC.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                miningWalletBTCActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane4, javax.swing.GroupLayout.DEFAULT_SIZE, 1237, Short.MAX_VALUE)
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addComponent(jLabel3)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(miningPayoutWas))
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addComponent(jPanel6, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel3Layout.createSequentialGroup()
                                .addComponent(jLabel14)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(miningWalletUSD, javax.swing.GroupLayout.PREFERRED_SIZE, 130, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jLabel31)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(miningWalletBTC, javax.swing.GroupLayout.PREFERRED_SIZE, 124, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(totalInMining))
                            .addGroup(jPanel3Layout.createSequentialGroup()
                                .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, 631, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(0, 0, Short.MAX_VALUE)))))
                .addContainerGap())
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel3)
                    .addComponent(miningPayoutWas))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane4, javax.swing.GroupLayout.PREFERRED_SIZE, 118, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(totalInMining)
                            .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(jLabel14)
                                .addComponent(miningWalletUSD, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(jLabel31)
                                .addComponent(miningWalletBTC, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, 267, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jPanel6, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );

        jTabbedPane1.addTab("Mining", jPanel3);

        jPanel7.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createTitledBorder("Money at exchanges"), "Money at exchanges"));

        jLabel18.setText("zqw10k");

        jLabel19.setText("zqw11k");

        jLabel20.setText("zqw12k");

        jLabel21.setText("Amount in exchange");

        exchange3In.setText("0.00");
        exchange3In.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exchange3InActionPerformed(evt);
            }
        });

        exchange2In.setText("0.00");
        exchange2In.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exchange2InActionPerformed(evt);
            }
        });

        exchange1In.setText("0.00");
        exchange1In.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exchange1InActionPerformed(evt);
            }
        });

        jLabel22.setText("Optimal amount");

        exchange1Opt.setText("$0.00");

        exchange2Opt.setText("$0.00");

        exchange3Opt.setText("$0.00");

        jLabel26.setText("Difference");

        jLabel27.setText("USD");

        exchange1Diff.setText("$0.00");

        exchange2Diff.setText("$0.00");

        exchange3Diff.setText("$0.00");

        jLabel29.setText("Total");

        totalTrading.setText("$0.00");

        javax.swing.GroupLayout jPanel7Layout = new javax.swing.GroupLayout(jPanel7);
        jPanel7.setLayout(jPanel7Layout);
        jPanel7Layout.setHorizontalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel7Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel20)
                    .addComponent(jLabel18)
                    .addComponent(jLabel19)
                    .addComponent(jLabel27)
                    .addComponent(jLabel29))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(totalTrading)
                    .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                        .addComponent(jLabel21, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(exchange2In)
                        .addComponent(exchange3In)
                        .addComponent(exchange1In)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jLabel22, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(exchange1Opt, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(exchange2Opt, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(exchange3Opt, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel26, javax.swing.GroupLayout.DEFAULT_SIZE, 159, Short.MAX_VALUE)
                    .addComponent(exchange1Diff, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(exchange2Diff, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(exchange3Diff, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel7Layout.setVerticalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel7Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel21)
                    .addComponent(jLabel22)
                    .addComponent(jLabel26)
                    .addComponent(jLabel27))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel18)
                    .addComponent(exchange1In, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(exchange1Opt)
                    .addComponent(exchange1Diff))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel19)
                    .addComponent(exchange2In, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(exchange2Opt)
                    .addComponent(exchange2Diff))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel20)
                    .addComponent(exchange3In, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(exchange3Opt)
                    .addComponent(exchange3Diff))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel29)
                    .addComponent(totalTrading)))
        );

        jPanel8.setBorder(javax.swing.BorderFactory.createTitledBorder("Money distribution"));

        jLabel15.setText("zqw10k");

        jLabel16.setText("zqw11k");

        jLabel17.setText("zqw12k");

        exchange3Dist.setModel(new javax.swing.SpinnerNumberModel(1, 0, null, 1));
        exchange3Dist.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                exchange3DistStateChanged(evt);
            }
        });

        exchange2Dist.setModel(new javax.swing.SpinnerNumberModel(1, 0, null, 1));
        exchange2Dist.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                exchange2DistStateChanged(evt);
            }
        });

        exchange1Dist.setModel(new javax.swing.SpinnerNumberModel(1, 0, null, 1));
        exchange1Dist.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                exchange1DistStateChanged(evt);
            }
        });

        javax.swing.GroupLayout jPanel8Layout = new javax.swing.GroupLayout(jPanel8);
        jPanel8.setLayout(jPanel8Layout);
        jPanel8Layout.setHorizontalGroup(
            jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel8Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel17)
                    .addComponent(jLabel16)
                    .addComponent(jLabel15))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(exchange2Dist, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 50, Short.MAX_VALUE)
                    .addComponent(exchange3Dist, javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(exchange1Dist))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel8Layout.setVerticalGroup(
            jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel8Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel15)
                    .addComponent(exchange1Dist, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel16)
                    .addComponent(exchange2Dist, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel17)
                    .addComponent(exchange3Dist, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel12.setBorder(javax.swing.BorderFactory.createTitledBorder("Trade earnings"));

        tradeEarningsSince.setText("Earnings since: 01/01/2009");

        jLabel47.setText("zqw10k");

        jLabel48.setText("zqw11k");

        jLabel49.setText("zqw12k");

        jLabel50.setText("Total");

        tradeExchange3Earnings.setText("$0.00");

        tradeExchange1Earnings.setText("$0.00");

        tradeExchange2Earnings.setText("$0.00");

        tradeEarnings.setText("$0.00");

        tradeExchange1EarningsPercent.setText("%0.00");

        tradeExchange2EarningsPercent.setText("%0.00");

        tradeExchange3EarningsPercent.setText("%0.00");

        tradeEarningsPercent.setText("%0.00");

        tradeEarningsDays.setText("0 days");

        tradeExchange1EarningsPerDay.setText("$0.00 /day");

        tradeExchange2EarningsPerDay.setText("$0.00 /day");

        tradeExchange3EarningsPerDay.setText("$0.00 /day");

        tradeEarningsPerDay.setText("$0.00 /day");

        javax.swing.GroupLayout jPanel12Layout = new javax.swing.GroupLayout(jPanel12);
        jPanel12.setLayout(jPanel12Layout);
        jPanel12Layout.setHorizontalGroup(
            jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel12Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(tradeEarningsSince, javax.swing.GroupLayout.DEFAULT_SIZE, 202, Short.MAX_VALUE)
                    .addGroup(jPanel12Layout.createSequentialGroup()
                        .addGroup(jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel49)
                            .addComponent(jLabel47)
                            .addComponent(jLabel48)
                            .addComponent(jLabel50))
                        .addGap(18, 18, 18)
                        .addGroup(jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(tradeExchange1Earnings, javax.swing.GroupLayout.DEFAULT_SIZE, 124, Short.MAX_VALUE)
                            .addComponent(tradeExchange2Earnings, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(tradeExchange3Earnings, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(tradeEarnings, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(tradeExchange1EarningsPercent, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(tradeEarningsPercent, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(tradeExchange2EarningsPercent, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(tradeExchange3EarningsPercent, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(tradeEarningsDays, javax.swing.GroupLayout.DEFAULT_SIZE, 124, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(tradeExchange1EarningsPerDay, javax.swing.GroupLayout.DEFAULT_SIZE, 124, Short.MAX_VALUE)
                    .addComponent(tradeExchange2EarningsPerDay, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(tradeExchange3EarningsPerDay, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(tradeEarningsPerDay, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel12Layout.setVerticalGroup(
            jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel12Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(tradeEarningsSince)
                    .addComponent(tradeEarningsDays))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel47)
                    .addComponent(tradeExchange1Earnings)
                    .addComponent(tradeExchange1EarningsPercent)
                    .addComponent(tradeExchange1EarningsPerDay))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel48)
                    .addComponent(tradeExchange2Earnings)
                    .addComponent(tradeExchange2EarningsPercent)
                    .addComponent(tradeExchange2EarningsPerDay))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel49)
                    .addComponent(tradeExchange3Earnings)
                    .addComponent(tradeExchange3EarningsPercent)
                    .addComponent(tradeExchange3EarningsPerDay))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel50)
                    .addComponent(tradeEarnings)
                    .addComponent(tradeEarningsPercent)
                    .addComponent(tradeEarningsPerDay))
                .addContainerGap())
        );

        jPanel13.setBorder(javax.swing.BorderFactory.createTitledBorder("API keys"));

        jLabel36.setText("zqw10k");

        exchange1ConnectButton.setText("Connect");
        exchange1ConnectButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exchange1ConnectButtonActionPerformed(evt);
            }
        });

        jLabel39.setText("zqw11k");

        jLabel40.setText("zqw12k");

        exchange2ConnectButton.setText("Connect");
        exchange2ConnectButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exchange2ConnectButtonActionPerformed(evt);
            }
        });

        exchange1Status.setText("Not connected");

        exchange2Status.setText("Not connected");

        exchange3Status.setText("Not connected");

        exchange3ConnectButton.setText("Connect");
        exchange3ConnectButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exchange3ConnectButtonActionPerformed(evt);
            }
        });

        jLabel53.setText("Add API keys to automatically download exchange balances.");

        javax.swing.GroupLayout jPanel13Layout = new javax.swing.GroupLayout(jPanel13);
        jPanel13.setLayout(jPanel13Layout);
        jPanel13Layout.setHorizontalGroup(
            jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel13Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(jPanel13Layout.createSequentialGroup()
                        .addGroup(jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addGroup(jPanel13Layout.createSequentialGroup()
                                .addGap(72, 72, 72)
                                .addComponent(exchange3ApiSecret, javax.swing.GroupLayout.PREFERRED_SIZE, 248, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(jPanel13Layout.createSequentialGroup()
                                .addComponent(jLabel40)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(exchange3ApiKey, javax.swing.GroupLayout.PREFERRED_SIZE, 248, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(exchange3Status, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addGroup(jPanel13Layout.createSequentialGroup()
                                .addComponent(exchange3ConnectButton, javax.swing.GroupLayout.PREFERRED_SIZE, 74, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(0, 225, Short.MAX_VALUE))))
                    .addComponent(jLabel53)
                    .addGroup(jPanel13Layout.createSequentialGroup()
                        .addGroup(jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel39)
                            .addComponent(jLabel36))
                        .addGap(33, 33, 33)
                        .addGroup(jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel13Layout.createSequentialGroup()
                                .addComponent(exchange1ApiSecret, javax.swing.GroupLayout.PREFERRED_SIZE, 248, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(exchange1Status, javax.swing.GroupLayout.PREFERRED_SIZE, 74, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(jPanel13Layout.createSequentialGroup()
                                .addGroup(jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(exchange2ApiKey, javax.swing.GroupLayout.DEFAULT_SIZE, 248, Short.MAX_VALUE)
                                    .addComponent(exchange2ApiSecret))
                                .addGap(12, 12, 12)
                                .addGroup(jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(exchange2ConnectButton)
                                    .addComponent(exchange2Status, javax.swing.GroupLayout.PREFERRED_SIZE, 74, javax.swing.GroupLayout.PREFERRED_SIZE)))
                            .addGroup(jPanel13Layout.createSequentialGroup()
                                .addComponent(exchange1ApiKey, javax.swing.GroupLayout.PREFERRED_SIZE, 248, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(exchange1ConnectButton)))))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel13Layout.setVerticalGroup(
            jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel13Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel36)
                    .addComponent(exchange1ApiKey, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(exchange1ConnectButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(exchange1ApiSecret, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(exchange1Status))
                .addGap(18, 18, 18)
                .addGroup(jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(exchange2ApiKey, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(exchange2ConnectButton)
                    .addComponent(jLabel39))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(exchange2ApiSecret, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(exchange2Status))
                .addGap(18, 18, 18)
                .addGroup(jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(exchange3ApiKey, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel40)
                    .addComponent(exchange3ConnectButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(exchange3ApiSecret, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(exchange3Status))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 20, Short.MAX_VALUE)
                .addComponent(jLabel53)
                .addContainerGap())
        );

        tradeLogBox.setEditable(false);
        tradeLogBox.setColumns(20);
        tradeLogBox.setRows(5);
        jScrollPane5.setViewportView(tradeLogBox);

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(jPanel13, javax.swing.GroupLayout.PREFERRED_SIZE, 450, Short.MAX_VALUE)
                    .addComponent(jPanel7, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addComponent(jPanel12, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jPanel8, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jScrollPane5))
                .addContainerGap(138, Short.MAX_VALUE))
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jPanel12, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel7, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel8, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jPanel13, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jScrollPane5, javax.swing.GroupLayout.PREFERRED_SIZE, 257, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        jTabbedPane1.addTab("Trading", jPanel4);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jTabbedPane1)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jTabbedPane1)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void addCoinButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addCoinButtonActionPerformed
        for (int i = 0; i < cap.size(); i++) {
            if (cap.get(i).get("name").equals(this.coinComboBox.getSelectedItem())) {
                cap.get(i).put("amount", this.coinQuantity.getText());
                break;
            }
        }
        printPortofolio();
        this.coinPortefolioArea.setCaretPosition(0);
        autosaveHoldingData();
    }//GEN-LAST:event_addCoinButtonActionPerformed

    private void addContractFeeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addContractFeeButtonActionPerformed
        HashMap fee = new HashMap();
        fee.put("type", this.contractTypeComboBox.getSelectedItem().toString());
        fee.put("price_usd", this.contractPrice.getText());
        fee.put("days", this.contractDays.getText());
        fee.put("price_btc", this.contractBitcoinPrice.getText());
        fee.put("startDate", this.contractStartDate.getText());
        feeData.add(fee);
        calculateFees();
        autosaveMiningData();
    }//GEN-LAST:event_addContractFeeButtonActionPerformed

    private void viewContractFeesButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewContractFeesButtonActionPerformed
        if (isNull(fds)) {
            fds = new FeeDataScreen();
        }
        fds.setParent(this);
        fds.setVisible(true);
    }//GEN-LAST:event_viewContractFeesButtonActionPerformed

    private void contractTypeComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_contractTypeComboBoxActionPerformed
        for (int i = 0; i < this.contractDataTable.getRowCount(); i++) {
            if (this.contractDataTable.getModel().getValueAt(i, 0).toString().equals(this.contractTypeComboBox.getSelectedItem().toString())) {
                this.contractPrice.setText(this.contractDataTable.getValueAt(i, 1).toString());
                this.contractDays.setText(this.contractDataTable.getValueAt(i, 3).toString());
                break;
            }
        }
    }//GEN-LAST:event_contractTypeComboBoxActionPerformed

    private void miningWalletUSDActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_miningWalletUSDActionPerformed
        for (int i = 0; i < cap.size(); i++) {
            if (cap.get(i).get("name").toString().equals("Bitcoin")) {
                this.miningWalletBTC.setText(new BigDecimal(this.miningWalletUSD.getText()).divide(new BigDecimal(cap.get(i).get("price_usd").toString()), mc).toPlainString());
                break;
            }
        }
        updateMiningTotal();
        calculateWhatToBuyMining();
        autosaveMiningData();
    }//GEN-LAST:event_miningWalletUSDActionPerformed

    private void exchange1InActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exchange1InActionPerformed
        updateTrade();
        updateStats();
        autosaveTradingData();
    }//GEN-LAST:event_exchange1InActionPerformed

    private void exchange2InActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exchange2InActionPerformed
        updateTrade();
        updateStats();
        autosaveTradingData();
    }//GEN-LAST:event_exchange2InActionPerformed

    private void exchange3InActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exchange3InActionPerformed
        updateTrade();
        updateStats();
        autosaveTradingData();
    }//GEN-LAST:event_exchange3InActionPerformed

    private void holdingDistributionStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_holdingDistributionStateChanged
        updateDashboard(true);
        autosaveDashboardData();
    }//GEN-LAST:event_holdingDistributionStateChanged

    private void miningDistributionStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_miningDistributionStateChanged
        updateDashboard(true);
        autosaveDashboardData();
    }//GEN-LAST:event_miningDistributionStateChanged

    private void tradingDistributionStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_tradingDistributionStateChanged
        updateDashboard(true);
        autosaveDashboardData();
    }//GEN-LAST:event_tradingDistributionStateChanged

    private void exchange1DistStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_exchange1DistStateChanged
        updateTrade();
        autosaveTradingData();
    }//GEN-LAST:event_exchange1DistStateChanged

    private void exchange2DistStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_exchange2DistStateChanged
        updateTrade();
        autosaveTradingData();
    }//GEN-LAST:event_exchange2DistStateChanged

    private void exchange3DistStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_exchange3DistStateChanged
        updateTrade();
        autosaveTradingData();
    }//GEN-LAST:event_exchange3DistStateChanged

    private void contractDataTablePropertyChange(java.beans.PropertyChangeEvent evt) {//GEN-FIRST:event_contractDataTablePropertyChange
        if (!startup) {
            calculateFees();
            updateMiningTotal();
            calculateWhatToBuyMining();
            autosaveMiningData();
        }
    }//GEN-LAST:event_contractDataTablePropertyChange

    private void modifyEarningsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_modifyEarningsButtonActionPerformed
        switch (this.earningTypeToModify.getSelectedIndex()) {
            case 0:
                earningsHoldStart = earningsHoldStart.add(new BigDecimal(this.earningAmountToModify.getText()));
                break;
            case 1:
                earningsMineStart = earningsMineStart.add(new BigDecimal(this.earningAmountToModify.getText()));
                break;
            case 2:
                earningsTradeStart = earningsTradeStart.add(new BigDecimal(this.earningAmountToModify.getText()));
                break;
        }
        updateStats();
        this.earningAmountToModify.setText("0.00");
        autosaveStatData();
    }//GEN-LAST:event_modifyEarningsButtonActionPerformed

    private void resetAllEarningsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetAllEarningsButtonActionPerformed
        earningsDateStart = new Date();
        earningsHoldStart = new BigDecimal(0).add(haveHold);
        earningsMineStart = new BigDecimal(0).add(haveMine);
        earningsTradeStart = new BigDecimal(0).add(haveTrade);
        earningsExchange1Start = new BigDecimal(this.exchange1In.getText());
        earningsExchange2Start = new BigDecimal(this.exchange2In.getText());
        earningsExchange3Start = new BigDecimal(this.exchange3In.getText());
        updateStats();
        autosaveStatData();
    }//GEN-LAST:event_resetAllEarningsButtonActionPerformed

    private void miningWalletBTCActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_miningWalletBTCActionPerformed
        for (int i = 0; i < cap.size(); i++) {
            if (cap.get(i).get("name").toString().equals("Bitcoin")) {
                this.miningWalletUSD.setText(formatBigDecimal(new BigDecimal(this.miningWalletBTC.getText()).multiply(new BigDecimal(cap.get(i).get("price_usd").toString()), mc), "", 2));
                break;
            }
        }
        updateMiningTotal();
        calculateWhatToBuyMining();
        autosaveMiningData();
    }//GEN-LAST:event_miningWalletBTCActionPerformed

    private void exchange1ConnectButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exchange1ConnectButtonActionPerformed
        connectToExchange(1);
        updateTrade();
        autosaveTradingData();
    }//GEN-LAST:event_exchange1ConnectButtonActionPerformed

    private void exchange2ConnectButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exchange2ConnectButtonActionPerformed
        connectToExchange(2);
        updateTrade();
        autosaveTradingData();
    }//GEN-LAST:event_exchange2ConnectButtonActionPerformed

    private void exchange3ConnectButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exchange3ConnectButtonActionPerformed
        connectToExchange(3);
        updateTrade();
        autosaveTradingData();
    }//GEN-LAST:event_exchange3ConnectButtonActionPerformed

    private void anitaInvestmentStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_anitaInvestmentStateChanged
        updateDashboard(true);
        autosaveDashboardData();
    }//GEN-LAST:event_anitaInvestmentStateChanged

    private void woutInvestmentStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_woutInvestmentStateChanged
        updateDashboard(true);
        autosaveDashboardData();
    }//GEN-LAST:event_woutInvestmentStateChanged

    private void jPanel9MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jPanel9MouseClicked
        changeCurrency();
    }//GEN-LAST:event_jPanel9MouseClicked

    private void euroDollarExchangeMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_euroDollarExchangeMouseClicked
        changeCurrencyConv();
    }//GEN-LAST:event_euroDollarExchangeMouseClicked

    private void filterPortofolioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_filterPortofolioButtonActionPerformed
        autosaveHoldingData();
    }//GEN-LAST:event_filterPortofolioButtonActionPerformed

    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(MainScreen.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(MainScreen.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(MainScreen.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(MainScreen.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new MainScreen().setVisible(true);
            }
        });
    }

    private void readFromCoinMarketCap(String url, boolean everything) throws IOException {
        boolean success = false;
        InputStream is = null;
        do {
            try {
                is = new URL(url).openStream();
                BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
                boolean noAdd = true;
                boolean getEURUSD = everything;
                String line;
                int i = 0;
                while ((line = rd.readLine()) != null) {
                    if (line.contains("\"name\":")) {
                        String temp = line.split("\": \"")[1];
                        cap.add(new HashMap<>());
                        cap_original.add(new HashMap<>());
                        boolean hasBeenAdded = true;
                        addToCap(line, "name");
                        cap.get(cap.size() - 1).put("amount", "0.0");
                        noAdd = !hasBeenAdded;
                    }
                    if (line.contains("\"symbol\":") && !noAdd) {
                        addToCap(line, "symbol");
                    }
                    if (line.contains("\"rank\":") && !noAdd) {
                        addToCap(line, "rank");
                    }
                    if (line.contains("\"price_usd\":") && !noAdd) {
                        addToCap(line, "price_usd");
                        if (getEURUSD) {
                            euro2Dollar = new BigDecimal(cap.get(0).get("price_usd").toString());
                            btc2Dollar = new BigDecimal(cap.get(0).get("price_usd").toString());
                            ars2Dollar = new BigDecimal(cap.get(0).get("price_usd").toString());
                            ars2Dollar = ars2Dollar.divide(btc2Ars, mc); //1 ARS = x USD
                        }
                    }
                    if (line.contains("\"price_btc\":") && !noAdd) {
                        addToCap(line, "price_btc");
                    }
                    if (line.contains("\"market_cap_usd\":") && !noAdd) {
                        addToCap(line, "market_cap_usd");
                    }
                    if (line.contains("\"price_eur\":") && getEURUSD) {
                        String temp = line.split("\": \"")[1];
                        euro2Dollar = euro2Dollar.divide(new BigDecimal(temp.substring(0, temp.length() - 3)), mc);
                        this.euroDollarExchange.setText("$/€: " + euro2Dollar.toPlainString());
                        getEURUSD = false;
                    }
                }
                success = true;
            } catch (Exception e) {
                System.out.println("Error occured during connection to Coin Market Cap: " + e.getMessage() + ". Retrying...");
            } finally {
                if (!isNull(is)) {
                    is.close();
                }
            }
        } while (!success);
    }

    private void readFromCoinMarketCapArs(String url) throws IOException {
        boolean success = false;
        InputStream is = null;
        do {
            try {
                is = new URL(url).openStream();
                BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
                String line;
                while ((line = rd.readLine()) != null) {
                    if (line.contains("\"price_ars\":")) {
                        String temp = line.split("\": \"")[1];
                        btc2Ars = new BigDecimal(temp.substring(0, temp.length() - 3));
                    }
                }
                success = true;
            } catch (Exception e) {
                System.out.println("Error occured during connection to Coin Market Cap (ARS) " + e.getMessage() + ". Retrying...");
            } finally {
                if (!isNull(is)) {
                    is.close();
                }
            }
        } while (!success);
    }

    private void readHistoricalBitcoinData(String url) {
        boolean success = false;
        InputStream is = null;
        int nb_try = 0;
        do {
            try {
                is = new URL(url).openStream();
                BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
                String line;
                while ((line = rd.readLine()) != null) {
                    line = line.replaceAll("[\"{}]", "");
                    String[] temp = line.split(",");
                    for (int i = 0; i < temp.length; i++) {
                        String[] temp2 = temp[i].split(":");
                        if (temp2.length == 2) {
                            bitcoinHistory.put(temp2[0], temp2[1]);
                        }
                    }
                }
                success = true;
                gotFeeData = true;
            } catch (Exception e) {
                if (nb_try < 5) {
                    System.out.println("Error occured during connection to Bitcoin Venezuela: " + e.getMessage() + ". Retrying...");
                    nb_try++;
                } else {
                    System.out.println("Couldn't connect to Bitcoin Venezuela: " + e.getMessage() + ". Ignoring...");
                    this.jLabel3.setText("Couldn't connect to Bitcoin Venezuela, values are inaccurate for mining.");
                    success = true;
                    gotFeeData = false;
                }
            } finally {
                if (!isNull(is)) {
                    try {
                        is.close();
                    } catch (IOException ex) {
                        Logger.getLogger(MainScreen.class.getName()).log(Level.WARNING, null, ex);
                    }
                }
            }
        } while (!success);
    }

    private void addToCap(String line, String key) {
        line = line.replace("\": null", "\": \"0.00\"");
        String temp = line.split("\": \"")[1];
        cap.get(cap.size() - 1).put(key, temp.substring(0, temp.length() - 3));
        cap_original.get(cap_original.size() - 1).put(key, temp.substring(0, temp.length() - 3));
    }

    private void autosaveHoldingData() {
        if (!startup) {
            FileOutputStream f = null;
            ObjectOutputStream o = null;
            try {
                f = new FileOutputStream(new File("holding.dat"));
                o = new ObjectOutputStream(f);
                HashMap hm = new HashMap();
                hm.put("cap", cap);
                hm.put("enterPercentage", this.enterPercentage.getText());
                hm.put("exitPercentage", this.exitPercentage.getText());
                o.writeObject(hm);
            } catch (FileNotFoundException ex) {

            } catch (IOException ex) {

            } finally {
                try {
                    if (!isNull(o)) {
                        o.close();
                    }
                    if (!isNull(f)) {
                        f.close();
                    }
                } catch (IOException ex) {

                }
            }
        }
    }

    private void autosaveMiningData() {
        if (!startup && gotFeeData) {
            FileOutputStream f = null;
            ObjectOutputStream o = null;
            try {
                f = new FileOutputStream(new File("mining.dat"));
                o = new ObjectOutputStream(f);
                HashMap hm = new HashMap();
                for (int i = 0; i < this.contractDataTable.getRowCount(); i++) {
                    hm.put("contractData_priceContract_" + i, this.contractDataTable.getModel().getValueAt(i, 1).toString());
                    hm.put("contractData_hashpowerContract_" + i, this.contractDataTable.getModel().getValueAt(i, 2).toString());
                    hm.put("contractData_days_" + i, this.contractDataTable.getModel().getValueAt(i, 3).toString());
                    hm.put("contractData_moneyIn_" + i, this.contractDataTable.getModel().getValueAt(i, 5).toString());
                    hm.put("contractData_yourHashpower_" + i, this.contractDataTable.getModel().getValueAt(i, 6).toString());
                    hm.put("contractData_lastPayout_" + i, this.contractDataTable.getModel().getValueAt(i, 7).toString());
                    hm.put("contractData_currency_" + i, String.valueOf(this.contractDataTable.getModel().getValueAt(i, 8)));
                    hm.put("contractData_isAvailable_" + i, String.valueOf(this.contractDataTable.getModel().getValueAt(i, 10)));
                }
                hm.put("miningWalletBTC", this.miningWalletBTC.getText());
                hm.put("feeData", feeData);
                o.writeObject(hm);
            } catch (FileNotFoundException ex) {

            } catch (IOException ex) {

            } finally {
                try {
                    if (!isNull(o)) {
                        o.close();
                    }
                    if (!isNull(f)) {
                        f.close();
                    }
                } catch (IOException ex) {

                }
            }
        }
    }

    private void autosaveTradingData() {
        if (!startup) {
            FileOutputStream f = null;
            ObjectOutputStream o = null;
            try {
                f = new FileOutputStream(new File("trading.dat"));
                o = new ObjectOutputStream(f);
                HashMap hm = new HashMap();
                hm.put("exchange1", this.exchange1In.getText());
                hm.put("exchange2", this.exchange2In.getText());
                hm.put("exchange3", this.exchange3In.getText());
                hm.put("dist1", (int) this.exchange1Dist.getValue());
                hm.put("dist2", (int) this.exchange2Dist.getValue());
                hm.put("dist3", (int) this.exchange3Dist.getValue());
                hm.put("exchange1ApiKey", this.exchange1ApiKey.getText());
                hm.put("exchange2ApiKey", this.exchange2ApiKey.getText());
                hm.put("exchange3ApiKey", this.exchange3ApiKey.getText());
                hm.put("exchange1ApiSecret", this.exchange1ApiSecret.getText());
                hm.put("exchange2ApiSecret", this.exchange2ApiSecret.getText());
                hm.put("exchange3ApiSecret", this.exchange3ApiSecret.getText());
                o.writeObject(hm);
            } catch (FileNotFoundException ex) {

            } catch (IOException ex) {

            } finally {
                try {
                    if (!isNull(o)) {
                        o.close();
                    }
                    if (!isNull(f)) {
                        f.close();
                    }
                } catch (IOException ex) {

                }
            }
        }
    }

    private void autosaveDashboardData() {
        if (!startup) {
            FileOutputStream f = null;
            ObjectOutputStream o = null;
            try {
                f = new FileOutputStream(new File("dashboard.dat"));
                o = new ObjectOutputStream(f);
                HashMap hm = new HashMap();
                hm.put("holdingDistribution", (int) this.holdingDistribution.getValue());
                hm.put("miningDistribution", (int) this.miningDistribution.getValue());
                hm.put("tradingDistribution", (int) this.tradingDistribution.getValue());
                hm.put("anitaInvestment", (Long) this.anitaInvestment.getValue());
                hm.put("woutInvestment", (Long) this.woutInvestment.getValue());
                o.writeObject(hm);
            } catch (FileNotFoundException ex) {

            } catch (IOException ex) {

            } finally {
                try {
                    if (!isNull(o)) {
                        o.close();
                    }
                    if (!isNull(f)) {
                        f.close();
                    }
                } catch (IOException ex) {

                }
            }
        }
    }

    private void autosaveStatData() {
        if (!startup) {
            FileOutputStream f = null;
            ObjectOutputStream o = null;
            try {
                f = new FileOutputStream(new File("stat.dat"));
                o = new ObjectOutputStream(f);
                HashMap hm = new HashMap();
                hm.put("earningsDateStart", earningsDateStart);
                hm.put("earningsHoldStart", earningsHoldStart.toPlainString());
                hm.put("earningsMineStart", earningsMineStart.toPlainString());
                hm.put("earningsTradeStart", earningsTradeStart.toPlainString());
                hm.put("earningsExchange1Start", earningsExchange1Start.toPlainString());
                hm.put("earningsExchange2Start", earningsExchange2Start.toPlainString());
                hm.put("earningsExchange3Start", earningsExchange3Start.toPlainString());
                o.writeObject(hm);
            } catch (FileNotFoundException ex) {

            } catch (IOException ex) {

            } finally {
                try {
                    if (!isNull(o)) {
                        o.close();
                    }
                    if (!isNull(f)) {
                        f.close();
                    }
                } catch (IOException ex) {

                }
            }
        }
    }

    public void saveMiningData() {
        autosaveMiningData();
    }

    public ArrayList<HashMap> getFeeData() {
        return feeData;
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton addCoinButton;
    private javax.swing.JButton addContractFeeButton;
    private javax.swing.JLabel anitaEarnings;
    private javax.swing.JLabel anitaEarningsPerDay;
    private javax.swing.JLabel anitaEarningsPercent;
    private javax.swing.JSpinner anitaInvestment;
    private javax.swing.JLabel anitaTotalBitcoin;
    private javax.swing.JLabel anitaTotalDollar;
    private javax.swing.JLabel anitaTotalEuro;
    private javax.swing.JComboBox<String> coinComboBox;
    private javax.swing.JTextArea coinMarketCapsArea;
    private javax.swing.JTextArea coinPortefolioArea;
    private javax.swing.JTextField coinQuantity;
    private javax.swing.JTextField contractBitcoinPrice;
    private javax.swing.JTable contractDataTable;
    private javax.swing.JTextField contractDays;
    private javax.swing.JTextField contractPrice;
    private javax.swing.JTextField contractStartDate;
    private javax.swing.JComboBox<String> contractTypeComboBox;
    private javax.swing.JTextField earningAmountToModify;
    private javax.swing.JComboBox<String> earningTypeToModify;
    private javax.swing.JTextField enterPercentage;
    private javax.swing.JLabel euroDollarExchange;
    private javax.swing.JTextField exchange1ApiKey;
    private javax.swing.JTextField exchange1ApiSecret;
    private javax.swing.JButton exchange1ConnectButton;
    private javax.swing.JLabel exchange1Diff;
    private javax.swing.JSpinner exchange1Dist;
    private javax.swing.JTextField exchange1In;
    private javax.swing.JLabel exchange1Opt;
    private javax.swing.JLabel exchange1Status;
    private javax.swing.JTextField exchange2ApiKey;
    private javax.swing.JTextField exchange2ApiSecret;
    private javax.swing.JButton exchange2ConnectButton;
    private javax.swing.JLabel exchange2Diff;
    private javax.swing.JSpinner exchange2Dist;
    private javax.swing.JTextField exchange2In;
    private javax.swing.JLabel exchange2Opt;
    private javax.swing.JLabel exchange2Status;
    private javax.swing.JTextField exchange3ApiKey;
    private javax.swing.JTextField exchange3ApiSecret;
    private javax.swing.JButton exchange3ConnectButton;
    private javax.swing.JLabel exchange3Diff;
    private javax.swing.JSpinner exchange3Dist;
    private javax.swing.JTextField exchange3In;
    private javax.swing.JLabel exchange3Opt;
    private javax.swing.JLabel exchange3Status;
    private javax.swing.JTextField exitPercentage;
    private javax.swing.JButton filterPortofolioButton;
    private javax.swing.JLabel holdingDiff;
    private javax.swing.JSpinner holdingDistribution;
    private javax.swing.JLabel holdingHave;
    private javax.swing.JLabel holdingOptimal;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel14;
    private javax.swing.JLabel jLabel15;
    private javax.swing.JLabel jLabel16;
    private javax.swing.JLabel jLabel17;
    private javax.swing.JLabel jLabel18;
    private javax.swing.JLabel jLabel19;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel20;
    private javax.swing.JLabel jLabel21;
    private javax.swing.JLabel jLabel22;
    private javax.swing.JLabel jLabel23;
    private javax.swing.JLabel jLabel24;
    private javax.swing.JLabel jLabel25;
    private javax.swing.JLabel jLabel26;
    private javax.swing.JLabel jLabel27;
    private javax.swing.JLabel jLabel28;
    private javax.swing.JLabel jLabel29;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel30;
    private javax.swing.JLabel jLabel31;
    private javax.swing.JLabel jLabel32;
    private javax.swing.JLabel jLabel33;
    private javax.swing.JLabel jLabel34;
    private javax.swing.JLabel jLabel35;
    private javax.swing.JLabel jLabel36;
    private javax.swing.JLabel jLabel37;
    private javax.swing.JLabel jLabel38;
    private javax.swing.JLabel jLabel39;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel40;
    private javax.swing.JLabel jLabel41;
    private javax.swing.JLabel jLabel42;
    private javax.swing.JLabel jLabel43;
    private javax.swing.JLabel jLabel44;
    private javax.swing.JLabel jLabel45;
    private javax.swing.JLabel jLabel46;
    private javax.swing.JLabel jLabel47;
    private javax.swing.JLabel jLabel48;
    private javax.swing.JLabel jLabel49;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel50;
    private javax.swing.JLabel jLabel51;
    private javax.swing.JLabel jLabel52;
    private javax.swing.JLabel jLabel53;
    private javax.swing.JLabel jLabel54;
    private javax.swing.JLabel jLabel55;
    private javax.swing.JLabel jLabel56;
    private javax.swing.JLabel jLabel57;
    private javax.swing.JLabel jLabel58;
    private javax.swing.JLabel jLabel59;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel;
    private javax.swing.JPanel jPanel10;
    private javax.swing.JPanel jPanel11;
    private javax.swing.JPanel jPanel12;
    private javax.swing.JPanel jPanel13;
    private javax.swing.JPanel jPanel14;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JPanel jPanel7;
    private javax.swing.JPanel jPanel8;
    private javax.swing.JPanel jPanel9;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JScrollPane jScrollPane4;
    private javax.swing.JScrollPane jScrollPane5;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JList<String> miningBuyList;
    private javax.swing.JLabel miningDiff;
    private javax.swing.JSpinner miningDistribution;
    private javax.swing.JLabel miningHave;
    private javax.swing.JLabel miningOptimal;
    private javax.swing.JLabel miningPayoutWas;
    private javax.swing.JTextField miningWalletBTC;
    private javax.swing.JTextField miningWalletUSD;
    private javax.swing.JButton modifyEarningsButton;
    private javax.swing.JButton resetAllEarningsButton;
    private javax.swing.JLabel totalDashboardBitcoin;
    private javax.swing.JLabel totalDashboardDollar;
    private javax.swing.JLabel totalDashboardEuro;
    private javax.swing.JLabel totalEarnings;
    private javax.swing.JLabel totalEarningsDays;
    private javax.swing.JLabel totalEarningsPerDay;
    private javax.swing.JLabel totalEarningsPercent;
    private javax.swing.JLabel totalEarningsSince;
    private javax.swing.JLabel totalHoldingEarnings;
    private javax.swing.JLabel totalHoldingEarningsPerDay;
    private javax.swing.JLabel totalHoldingEarningsPercent;
    private javax.swing.JLabel totalInMining;
    private javax.swing.JLabel totalMiningEarnings;
    private javax.swing.JLabel totalMiningEarningsPerDay;
    private javax.swing.JLabel totalMiningEarningsPercent;
    private javax.swing.JLabel totalTrading;
    private javax.swing.JLabel totalTradingEarnings;
    private javax.swing.JLabel totalTradingEarningsPerDay;
    private javax.swing.JLabel totalTradingEarningsPercent;
    private javax.swing.JLabel tradeEarnings;
    private javax.swing.JLabel tradeEarningsDays;
    private javax.swing.JLabel tradeEarningsPerDay;
    private javax.swing.JLabel tradeEarningsPercent;
    private javax.swing.JLabel tradeEarningsSince;
    private javax.swing.JLabel tradeExchange1Earnings;
    private javax.swing.JLabel tradeExchange1EarningsPerDay;
    private javax.swing.JLabel tradeExchange1EarningsPercent;
    private javax.swing.JLabel tradeExchange2Earnings;
    private javax.swing.JLabel tradeExchange2EarningsPerDay;
    private javax.swing.JLabel tradeExchange2EarningsPercent;
    private javax.swing.JLabel tradeExchange3Earnings;
    private javax.swing.JLabel tradeExchange3EarningsPerDay;
    private javax.swing.JLabel tradeExchange3EarningsPercent;
    private javax.swing.JTextArea tradeLogBox;
    private javax.swing.JLabel tradingDiff;
    private javax.swing.JSpinner tradingDistribution;
    private javax.swing.JLabel tradingHave;
    private javax.swing.JLabel tradingOptimal;
    private javax.swing.JButton viewContractFeesButton;
    private javax.swing.JLabel woutEarnings;
    private javax.swing.JLabel woutEarningsPerDay;
    private javax.swing.JLabel woutEarningsPercent;
    private javax.swing.JSpinner woutInvestment;
    private javax.swing.JLabel woutTotalBitcoin;
    private javax.swing.JLabel woutTotalDollar;
    private javax.swing.JLabel woutTotalEuro;
    // End of variables declaration//GEN-END:variables
}
