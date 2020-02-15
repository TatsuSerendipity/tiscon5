package com.tiscon.dao;

import com.tiscon.domain.*;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import java.util.List;
//追加
import static java.lang.Math.*;
import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

/**
 * 引越し見積もり機能においてDBとのやり取りを行うクラス。
 *
 * @author Oikawa Yumi
 */
@Component
public class EstimateDao {

    /** データベース・アクセスAPIである「JDBC」を使い、名前付きパラメータを用いてSQLを実行するクラス */
    private final NamedParameterJdbcTemplate parameterJdbcTemplate;

    /**
     * コンストラクタ
     *
     * @param parameterJdbcTemplate NamedParameterJdbcTemplateクラス
     */
    public EstimateDao(NamedParameterJdbcTemplate parameterJdbcTemplate) {
        this.parameterJdbcTemplate = parameterJdbcTemplate;
    }

    /**
     * 顧客テーブルに登録する。
     *
     * @param customer 顧客情報
     * @return 登録件数
     */
    public int insertCustomer(Customer customer) {
        String sql = "INSERT INTO CUSTOMER(OLD_PREFECTURE_ID, NEW_PREFECTURE_ID, CUSTOMER_NAME, TEL, EMAIL, OLD_ADDRESS, NEW_ADDRESS)"
                + " VALUES(:oldPrefectureId, :newPrefectureId, :customerName, :tel, :email, :oldAddress, :newAddress)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        int resultNum = parameterJdbcTemplate.update(sql, new BeanPropertySqlParameterSource(customer), keyHolder);
        customer.setCustomerId(keyHolder.getKey().intValue());
        return resultNum;
    }

    /**
     * オプションサービス_顧客テーブルに登録する。
     *
     * @param optionService オプションサービス_顧客に登録する内容
     * @return 登録件数
     */
    public int insertCustomersOptionService(CustomerOptionService optionService) {
        String sql = "INSERT INTO CUSTOMER_OPTION_SERVICE(CUSTOMER_ID, SERVICE_ID)"
                + " VALUES(:customerId, :serviceId)";
        return parameterJdbcTemplate.update(sql, new BeanPropertySqlParameterSource(optionService));
    }

    /**
     * 顧客_荷物テーブルに登録する。
     *
     * @param packages 登録する荷物
     * @return 登録件数
     */
    public int[] batchInsertCustomerPackage(List<CustomerPackage> packages) {
        String sql = "INSERT INTO CUSTOMER_PACKAGE(CUSTOMER_ID, PACKAGE_ID, PACKAGE_NUMBER)"
                + " VALUES(:customerId, :packageId, :packageNumber)";
        SqlParameterSource[] batch = SqlParameterSourceUtils.createBatch(packages.toArray());

        return parameterJdbcTemplate.batchUpdate(sql, batch);
    }

    /**
     * 都道府県テーブルに登録されているすべての都道府県を取得する。
     *
     * @return すべての都道府県
     */
    public List<Prefecture> getAllPrefectures() {
        String sql = "SELECT PREFECTURE_ID, PREFECTURE_NAME FROM PREFECTURE";
        return parameterJdbcTemplate.query(sql,
                BeanPropertyRowMapper.newInstance(Prefecture.class));
    }

    /**
     * 都道府県間の距離を取得する。
     *
     * @param prefectureIdFrom 引っ越し元の都道府県
     * @param prefectureIdTo   引越し先の都道府県
     * @return 距離[km]
     */
    /*
    public double getDistance(String prefectureIdFrom, String prefectureIdTo) {
        // 都道府県のFromとToが逆転しても同じ距離となるため、「そのままの状態のデータ」と「FromとToを逆転させたデータ」をくっつけた状態で距離を取得する。
        String sql = "SELECT DISTANCE FROM (" +
                "SELECT PREFECTURE_ID_FROM, PREFECTURE_ID_TO, DISTANCE FROM PREFECTURE_DISTANCE UNION ALL " +
                "SELECT PREFECTURE_ID_TO PREFECTURE_ID_FROM ,PREFECTURE_ID_FROM PREFECTURE_ID_TO ,DISTANCE FROM PREFECTURE_DISTANCE) " +
                "WHERE PREFECTURE_ID_FROM  = :prefectureIdFrom AND PREFECTURE_ID_TO  = :prefectureIdTo";

        PrefectureDistance prefectureDistance = new PrefectureDistance();
        prefectureDistance.setPrefectureIdFrom(prefectureIdFrom);
        prefectureDistance.setPrefectureIdTo(prefectureIdTo);

        double distance;
        try {
            distance = parameterJdbcTemplate.queryForObject(sql, new BeanPropertySqlParameterSource(prefectureDistance), double.class);
        } catch (IncorrectResultSizeDataAccessException e) {
            distance = 0;
        }
        return distance;
    }
    */
    public double getDistance(String prefectureIdFrom, String prefectureIdTo, String oldaddress, String newaddress) {

        PrefectureDistance prefectureDistance = new PrefectureDistance();
        prefectureDistance.setPrefectureIdFrom(prefectureIdFrom);
        prefectureDistance.setPrefectureIdTo(prefectureIdTo);

        double r = 6378.137; // 赤道半径[km]

        String oldPre = prefectureIdFrom;
        String oldAdd = oldaddress;
        String newPre = prefectureIdTo;
        String newAdd = newaddress;
        // (lat = 緯度, lng = 経度)
        double latold = 0;
        double lngold = 0;
        double latnew = 0;
        double lngnew = 0;

        //oldPreとnewPreが1～9の場合は0を削る
        if (oldPre.equals("01") || oldPre.equals("02") || oldPre.equals("03") || oldPre.equals("04") || oldPre.equals("05") || oldPre.equals("06") || oldPre.equals("07") || oldPre.equals("08") || oldPre.equals("09")){
            oldPre = oldPre.substring(1);
        }
        if (newPre.equals("01") || newPre.equals("02") || newPre.equals("03") || newPre.equals("04") || newPre.equals("05") || newPre.equals("06") || newPre.equals("07") || newPre.equals("08") || newPre.equals("09")){
            newPre = newPre.substring(1);
        }

        try {
            //今はCドライブから読み込んでるが、本来はDBに緯度経度と市町村のファイルを登録し、そこから参照する
            //ファイルは resource/data/lonlat.csv に置いています
            File f = new File("C://lonlat.csv");
            BufferedReader br = new BufferedReader(new FileReader(f));

            String line;
            // 1行ずつCSVファイルを読み込み、転居先と転居元の緯度経度の抽出
            while ((line = br.readLine()) != null) {
                String[] data = line.split(",", 0); // 行をカンマ区切りで配列に変換
                if (data[0].equals(oldPre)) {
                    if (data[1].equals(oldAdd)) {
                        latold = Double.parseDouble(data[2]);
                        lngold = Double.parseDouble(data[3]);
                    }
                }
                if (data[0].equals(newPre)) {
                    if (data[1].equals(newAdd)) {
                        latnew = Double.parseDouble(data[2]);
                        lngnew = Double.parseDouble(data[3]);
                    }
                }
            }
            br.close();
        } catch (IOException e) {
            System.out.println(e);
        }
        
        //エラー処理
        //緯度と経度が抽出に失敗し、0.0のものがあればエラー
        //間に合いませんでした。

        //転居元の緯度経度
        double lat1 = latold * PI / 180;
        double lng1 = lngold * PI / 180;

        //転居先の緯度経度
        double lat2 = latnew * PI / 180;
        double lng2 = lngnew * PI / 180;

        // 2点間の距離[km]
        double distance = r * acos(sin(lat1) * sin(lat2) + cos(lat1) * cos(lat2) * cos(lng2 - lng1));

        return distance;
    }
    /**
     * 荷物ごとの段ボール数を取得する。
     *
     * @param packageId 荷物ID
     * @return 段ボール数
     */
    public int getBoxPerPackage(int packageId) {
        String sql = "SELECT BOX FROM PACKAGE_BOX WHERE PACKAGE_ID = :packageId";

        SqlParameterSource paramSource = new MapSqlParameterSource("packageId", packageId);
        return parameterJdbcTemplate.queryForObject(sql, paramSource, Integer.class);
    }

    /**
     * 段ボール数に応じたトラック料金を取得する。
     * DBから読み込んだMAX_BOXとPRICEを読み込み算出する
     * 今は配列の長さを[2][2]で固定しているが、本来はDBの長さを受け取って処理を変える
     * 行の長さだけループをすればうまくいくと思います。
     *
     * @param boxNum 総段ボール数
     * @return 料金[円]
     */
    public int getPricePerTruck(int boxNum) {
        //String sql = "SELECT PRICE FROM TRUCK_CAPACITY WHERE MAX_BOX >= :boxNum ORDER BY PRICE LIMIT 1";
        //SqlParameterSource paramSource = new MapSqlParameterSource("boxNum", boxNum);
        //return parameterJdbcTemplate.queryForObject(sql, paramSource, Integer.class);

        //段ボール数とトラック料金のオブジェクトに格納
        List<TruckCapacity> truckcapacity = gettruckPrice();
        //トラック料金を格納
        int truckPrice;

        int div = boxNum / truckcapacity.get(1).getmaxBox();
        int sur = boxNum % truckcapacity.get(1).getmaxBox();

        if(boxNum <= truckcapacity.get(0).getmaxBox()) {
            truckPrice = truckcapacity.get(0).getprice();
        }else if(boxNum <= truckcapacity.get(1).getmaxBox()){
            truckPrice = truckcapacity.get(1).getprice();
        }else if(sur <= truckcapacity.get(0).getmaxBox()){
            truckPrice = div * truckcapacity.get(1).getprice() + truckcapacity.get(0).getprice();
        }else{
            truckPrice = div * truckcapacity.get(1).getprice() + truckcapacity.get(1).getprice();
        }

        return truckPrice;

    }
    /**
     * トラックキャパシティテーブルに格納されているMax_BoxとPriceの取得
     *
     * @return 全てのMax_BoxとPrice
     */
    public List<TruckCapacity> gettruckPrice() {
        String sql = "SELECT MAX_BOX, PRICE FROM TRUCK_CAPACITY";
        return parameterJdbcTemplate.query(sql,
                BeanPropertyRowMapper.newInstance(TruckCapacity.class));
    }

    /**
     * オプションサービスの料金を取得する。
     *
     * @param serviceId サービスID
     * @return 料金
     */
    public int getPricePerOptionalService(int serviceId) {
        String sql = "SELECT PRICE FROM OPTIONAL_SERVICE WHERE SERVICE_ID = :serviceId";

        SqlParameterSource paramSource = new MapSqlParameterSource("serviceId", serviceId);
        return parameterJdbcTemplate.queryForObject(sql, paramSource, Integer.class);
    }
}
