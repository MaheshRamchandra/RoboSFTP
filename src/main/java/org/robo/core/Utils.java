package org.robo.core;


import javafx.scene.control.TextInputDialog;

import java.io.File;
import java.time.Duration;
import java.util.Optional;
import java.util.Random;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.time.Duration;

public class Utils {

    public static String generateNRIC() {
        // Configure Chrome to run headless
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless"); // run without GUI
        options.addArguments("--disable-gpu"); // optional
        options.addArguments("--window-size=1920,1080"); // ensure proper rendering

        WebDriver driver = new ChromeDriver(options);
        WebElement generateButton;
        try {
            // 1️ Open the NRIC generator page
            driver.get("https://samliew.com/nric-generator");


            // after creating the driver
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));

            // 1 Select the prefix from the dropdown
            WebElement prefixSelectElement = driver.findElement(By.id("firstchar")); // ID of the <select> element
            Select prefixSelect = new Select(prefixSelectElement);
            prefixSelect.selectByVisibleText("F"); // choose S, T, F, G, M as needed


            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            generateButton = wait.until(
                    ExpectedConditions.elementToBeClickable(By.id("gen"))
            );

            // 2 Click the "Generate" button
            //driver.findElement(By.id("gen")); // adjust ID if different
            generateButton.click();

            // 3 Get the NRIC value from the input box
            WebElement nricInput = driver.findElement(By.id("nric")); // adjust ID if different
            String generatedNric = nricInput.getAttribute("value");

            System.out.println("Generated NRIC (headless): " + generatedNric);
            return generatedNric;
        } finally {
            // 4 Close the browser
            driver.quit();
        }
    }

    public static void openFolder(File folder) {
        try {
            if (!folder.exists()) return;
            java.awt.Desktop.getDesktop().open(folder);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String promptForPassphrase(String message) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Passphrase");
        dialog.setHeaderText(message);
        Optional<String> res = dialog.showAndWait();
        return res.orElse(null);
    }
}
