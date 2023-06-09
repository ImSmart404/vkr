package com.mtuci.vkr.service;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mtuci.vkr.model.ExtendedInfo;
import com.mtuci.vkr.model.MainInfo;
import com.mtuci.vkr.model.PriceHistory;
import com.mtuci.vkr.repository.ExtendedInfoRepository;
import com.mtuci.vkr.repository.MainInfoRepository;
import com.mtuci.vkr.repository.PriceHistoryRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
@Slf4j
@AllArgsConstructor
public class WildBerriesService {
    MainInfoRepository mainInfoRepository;
    ExtendedInfoRepository extendedInfoRepository;
    PriceHistoryRepository priceHistoryRepository;
    public List<MainInfo> getProductsInfo(String request, Integer pages) throws InterruptedException, IOException, ExecutionException {
        log.info("Парсер начал работу");
        Set<Long> idSet = getId(request,pages);
        log.info("Получены id всех товаров");
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        List<MainInfo> mainInfoList;
        List<ExtendedInfo> extendedInfoList;
        Future<List<MainInfo>> mainInfoFuture = executorService.submit(() -> getMainInfoListByProductsId(idSet));
        Future<List<ExtendedInfo>> extendedInfoFuture = executorService.submit(() -> getExtendedInfoListByProductsId(idSet));
        mainInfoList = mainInfoFuture.get();
        extendedInfoList = extendedInfoFuture.get();
        executorService.shutdown();
        mainInfoRepository.saveAll(mainInfoList);
        log.info("Получена основная информация");
        log.info("Получена расширенная информация");
        for (ExtendedInfo extendedInfo : extendedInfoList) {
            List<PriceHistory> priceHistoryList = priceHistoryRepository.findByProductId(extendedInfo.getId());
            extendedInfo.setPriceHistory(priceHistoryList);
            extendedInfoRepository.save(extendedInfo);
        }
        log.info("Парсер завершил работу");
        return mainInfoList;
    }

    public Optional<ExtendedInfo> getProductExtendedInfo(Long id)  {

        return extendedInfoRepository.findById(id);
    }
    public List<ExtendedInfo> getExtendedInfoListByProductsId(Set<Long> idSet) throws IOException {
        List<ExtendedInfo> extendedInfoList = new ArrayList<>();
        List<Long> idListTemp = new ArrayList<>(idSet);
        Long id = 0L;
        while (!idListTemp.isEmpty()){
            List<PriceHistory> priceHistoryList = new ArrayList<>();
            ExtendedInfo extendedInfo = new ExtendedInfo();
            id = idListTemp.get(0);
            String basket = "10";
            Long vol = Long.parseLong(Long.toString(id).substring(0, 2));
            Long part = Long.parseLong(Long.toString(id).substring(0, 4));
            if (id/10000000 !=0){
                vol = Long.parseLong(Long.toString(id).substring(0, 3));
                part = Long.parseLong(Long.toString(id).substring(0, 5));
            }
            if (id / 100000000 != 0) {
                vol = Long.parseLong(Long.toString(id).substring(0, 4));
                part = Long.parseLong(Long.toString(id).substring(0, 6));
            }

            String urlForExtendedInfo = String.format("https://basket-%s.wb.ru/vol%d/part%d/%d/info/ru/card.json", basket, vol, part, id);
            URL objForExtendedInfo = new URL(urlForExtendedInfo);

            HttpURLConnection conForExtendedInfo = (HttpURLConnection) objForExtendedInfo.openConnection();

            conForExtendedInfo.setRequestMethod("GET");
            conForExtendedInfo.setRequestProperty("User-Agent", "Mozilla/5.0");

            int i = 11;
            while (conForExtendedInfo.getResponseCode() != HttpURLConnection.HTTP_OK){
                if(i>9){
                    basket =String.valueOf(i);
                } else {
                    basket = "0" + i;
                }
                urlForExtendedInfo = String.format("https://basket-%s.wb.ru/vol%d/part%d/%d/info/ru/card.json", basket, vol, part, id);
                objForExtendedInfo = new URL(urlForExtendedInfo);
                conForExtendedInfo = (HttpURLConnection) objForExtendedInfo.openConnection();
                conForExtendedInfo.setRequestMethod("GET");
                conForExtendedInfo.setRequestProperty("User-Agent", "Mozilla/5.0");
                i--;
            }

                String urlForPriceHistory = String.format("https://basket-%s.wb.ru/vol%d/part%d/%d/info/price-history.json", basket, vol, part, id);
                URL objForPriceHistory = new URL(urlForPriceHistory);
                HttpURLConnection conForPriceHistory = (HttpURLConnection) objForPriceHistory.openConnection();
                conForPriceHistory.setRequestMethod("GET");
                conForPriceHistory.setRequestProperty("User-Agent", "Mozilla/5.0");
                BufferedReader inForExtendedInfo = new BufferedReader(new InputStreamReader(conForExtendedInfo.getInputStream()));
                String inputLineForExtendedInfo;
                StringBuffer responseForExtendedInfo = new StringBuffer();
                while ((inputLineForExtendedInfo  = inForExtendedInfo.readLine()) != null) {
                    responseForExtendedInfo.append(inputLineForExtendedInfo );
                }

                BufferedReader inForPriceHistory;
                inForExtendedInfo.close();
                Gson gsonForExtendedInfo = new Gson();
                JsonObject jsonObjectForExtendedInfo  = gsonForExtendedInfo.fromJson(responseForExtendedInfo.toString(), JsonObject.class);
                try {
                extendedInfo.setId(id);
                extendedInfo.setFullName(jsonObjectForExtendedInfo.get("imt_name").getAsString());
                } catch (Exception ex) {
                    extendedInfo.setFullName("Продавец не установил название для данного товара");
                }
                try {
                    extendedInfo.setCategory(jsonObjectForExtendedInfo.get("subj_name").getAsString());
                } catch (Exception ex){
                    extendedInfo.setCategory("Продавец не установил категорию для данного товара");
                }
                try {
                    extendedInfo.setSubCategory( jsonObjectForExtendedInfo.get("subj_root_name").getAsString());
                } catch (Exception ex){
                    extendedInfo.setSubCategory("Продавец не установил категорию для данного товара");
                }
                try{
                    extendedInfo.setDescription(jsonObjectForExtendedInfo.get("description").getAsString());
                } catch (Exception ex){
                    extendedInfo.setDescription("Продавец не установил описание для данного товара");
                }

                JsonArray optionsArray = jsonObjectForExtendedInfo .getAsJsonArray("options");
                List<String> options = new ArrayList<>();
                for (JsonElement element : optionsArray) {
                    JsonObject optionObject = element.getAsJsonObject();
                    String optionName = optionObject.get("name").getAsString();
                    String optionValue = optionObject.get("value").getAsString();
                    options.add(optionName + ": " + optionValue);
                }
                String optionsJson = gsonForExtendedInfo.toJson(options);

                extendedInfo.setOptions(optionsJson);
            try{
                inForPriceHistory = new BufferedReader(new InputStreamReader(conForPriceHistory.getInputStream()));
                String inputLineForPriceHistory;
                StringBuilder responseForPriceHistory = new StringBuilder();
                while ((inputLineForPriceHistory  = inForPriceHistory.readLine()) != null) {
                    responseForPriceHistory.append(inputLineForPriceHistory );
                }
                Gson gsonForPriceHistory = new Gson();
                JsonArray jsonArrayForPriceHistory = gsonForPriceHistory.fromJson(responseForPriceHistory.toString(), JsonArray.class);
                for (JsonElement element : jsonArrayForPriceHistory) {
                    PriceHistory priceHistory = new PriceHistory();
                    JsonObject itemObject = element.getAsJsonObject();
                    JsonObject priceObject = itemObject.getAsJsonObject("price");
                    if (priceObject != null && priceObject.has("RUB")) {
                        Integer price = priceObject.get("RUB").getAsInt();
                        priceHistory.setProductId(id);
                        priceHistory.setPrice(price/100);
                        priceHistoryList.add(priceHistory);
                    }
                }

                inForPriceHistory.close();
            } catch (FileNotFoundException ex ){
                PriceHistory priceHistory = new PriceHistory();
                priceHistory.setProductId(id);
                priceHistory.setPrice(0);
                priceHistoryList.add(priceHistory);
                log.warn(ex.getMessage() + " История цен для данного товара не доступна {}", id );
            }
            if(priceHistoryRepository.findByProductId(extendedInfo.getId()).isEmpty()){
                priceHistoryRepository.saveAll(priceHistoryList);
            }
                extendedInfoList.add(extendedInfo);
                idListTemp.remove(0);
        }
        return extendedInfoList;
    }

    // Метод который возвращает преобразованные в обьект Product продукты по их id
    public List<MainInfo> getMainInfoListByProductsId(Set<Long> idSet) throws IOException {
        List<MainInfo> mainInfoList = new ArrayList<>();
        List<Long> idListTemp = new ArrayList<>(idSet);
        Long id = 0L;
        int i =0;
        while (!idListTemp.isEmpty()){
            MainInfo mainInfo = new MainInfo();
            id = idListTemp.get(0);
            String url = "https://card.wb.ru/cards/detail?appType=1&curr=rub&dest=-1257786&regions=80,64,38,4,115,83,33,68,70,69,30,86,75,40,1,66,48,110,31,22,71,114&spp=0&nm=" + id;
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("User-Agent", "Mozilla/5.0");
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            Gson gson = new Gson();
            JsonObject jsonObject = gson.fromJson(response.toString(), JsonObject.class);
            mainInfo.setId(id);
            mainInfo.setBrand(jsonObject.getAsJsonObject("data").getAsJsonArray("products").get(0).getAsJsonObject().get("brand").getAsString());
            mainInfo.setPriceU(jsonObject.getAsJsonObject("data").getAsJsonArray("products").get(0).getAsJsonObject().get("priceU").getAsLong()/100);
            mainInfo.setSalePrice(jsonObject.getAsJsonObject("data").getAsJsonArray("products").get(0).getAsJsonObject().get("salePriceU").getAsLong()/100);
            mainInfo.setRating(jsonObject.getAsJsonObject("data").getAsJsonArray("products").get(0).getAsJsonObject().get("rating").getAsInt());
            mainInfo.setPics(jsonObject.getAsJsonObject("data").getAsJsonArray("products").get(0).getAsJsonObject().get("pics").getAsInt());
            mainInfoList.add(mainInfo);
            idListTemp.remove(0);
        }
        return mainInfoList;
    }

    //Метод который по запросу пользователя парсит по указанному количеству страниц id всех товаров
    public Set<Long> getId(String request, Integer pages) throws InterruptedException {
        // Установка пути к драйверу Chrome
        System.setProperty("webdriver.chrome.driver", "src/main/resources/chromedriver.exe");
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");
        options.setHeadless(true);
        // Создание экземпляра WebDriver
        WebDriver driver = new ChromeDriver(options);
        String url = "https://www.wildberries.ru/catalog/0/search.aspx?search=" + request;
        driver.get(url);
        // Явное ожидание элемента
        Set<Long> idSet = new HashSet<>();
        List<WebElement> products;
        while (pages!=0){
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(3));
            By productSelector = By.className("product-card-list");
            WebElement productElement = wait.until(ExpectedConditions.visibilityOfElementLocated(productSelector));
            // Получение списка товаров
            products = productElement.findElements(By.cssSelector("a.product-card__link"));
            int numberOfProducts = products.size();
            WebDriverWait wait1 = new WebDriverWait(driver, Duration.ofSeconds(1));
            By productSelector1 = By.cssSelector("#catalog > div > div.pagination > div > a.pagination-next.pagination__next.j-next-page");
            // Цикл для прокручивания страницы до последнего элемента списка товаров
            while (true) {
                Actions actions = new Actions(driver);
                actions.moveToElement(products.get(numberOfProducts - 1));
                actions.perform();
                Thread.sleep(200); // замедление прокрутки на 200 милисекунд
                List<WebElement> updatedProducts = driver.findElements(By.cssSelector("a.product-card__link"));
                if (updatedProducts.size() == numberOfProducts) {
                    break;
                } else {
                    numberOfProducts = updatedProducts.size();
                    products = updatedProducts;
                }
            }
            // Запись ссылок и id всех товаров в List
            for (WebElement product : products) {
                String href = product.getAttribute("href");
                String id = (href.substring(href.lastIndexOf("g") + 2, href.lastIndexOf("d") - 1));
                idSet.add(Long.parseLong(id));
            }
            try {
                WebElement nextPageButton = wait1.until(ExpectedConditions.visibilityOfElementLocated(productSelector1));
                driver.get(nextPageButton.getAttribute("href"));
            } catch (TimeoutException ex){
                log.error("Ошибка: {}", ex.getMessage());
                break;
            }
            pages--;
        }
        driver.close();
        return idSet;
    }
    public ResponseEntity<byte[]> exportExtendedInfoToExel(ExtendedInfo extendedInfo) throws IOException {
        // Создание нового документа Excel
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("ExtendedInfo");

        // Заголовки столбцов
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("ID");
        headerRow.createCell(1).setCellValue("FullName");
        headerRow.createCell(2).setCellValue("Category");
        headerRow.createCell(3).setCellValue("SubCategory");
        headerRow.createCell(4).setCellValue("Options");
        headerRow.createCell(5).setCellValue("PriceHistory");
        headerRow.createCell(6).setCellValue("Description");

        // Данные
        Row dataRow = sheet.createRow(1);
        dataRow.createCell(0).setCellValue(extendedInfo.getId());
        dataRow.createCell(1).setCellValue(extendedInfo.getFullName());
        dataRow.createCell(2).setCellValue(extendedInfo.getCategory());
        dataRow.createCell(3).setCellValue(extendedInfo.getSubCategory());
        dataRow.createCell(4).setCellValue(extendedInfo.getOptions());
        dataRow.createCell(5).setCellValue(extendedInfo.getPriceHistory().toString());
        dataRow.createCell(6).setCellValue(extendedInfo.getDescription());

        // Установка автоматического размера столбцов
        for (int i = 0; i < 7; i++) {
            sheet.autoSizeColumn(i);
        }

        // Преобразование документа в массив байтов
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();

        byte[] fileContent = outputStream.toByteArray();

        // Отправка файла в ответе
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.attachment().filename("extended_info.xlsx").build());
        return new ResponseEntity<>(fileContent, headers, HttpStatus.OK);

    }

    public ResponseEntity<byte[]> exportMainInfoToExel(List<MainInfo> mainInfoList) throws IOException {
        // Создание нового документа Excel
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("MainInfo");

        // Заголовки столбцов
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("ID");
        headerRow.createCell(1).setCellValue("Price");
        headerRow.createCell(2).setCellValue("Sale Price");
        headerRow.createCell(3).setCellValue("Rating");
        headerRow.createCell(4).setCellValue("Brand");
        headerRow.createCell(5).setCellValue("Pictures");

        // Данные
        int rowIndex = 1;
        for (MainInfo mainInfo : mainInfoList) {
            Row dataRow = sheet.createRow(rowIndex++);
            dataRow.createCell(0).setCellValue(mainInfo.getId());
            dataRow.createCell(1).setCellValue(mainInfo.getPriceU());
            dataRow.createCell(2).setCellValue(mainInfo.getSalePrice());
            dataRow.createCell(3).setCellValue(mainInfo.getRating());
            dataRow.createCell(4).setCellValue(mainInfo.getBrand());
            dataRow.createCell(5).setCellValue(mainInfo.getPics());
        }

        // Установка автоматического размера столбцов
        for (int i = 0; i < 6; i++) {
            sheet.autoSizeColumn(i);
        }

        // Преобразование документа в массив байтов
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();

        byte[] fileContent = outputStream.toByteArray();

        // Отправка файла в ответе
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.attachment().filename("main_info.xlsx").build());
        return new ResponseEntity<>(fileContent, headers, HttpStatus.OK);
    }
}