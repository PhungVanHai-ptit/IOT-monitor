#include "DHT.h"
#include "WiFi.h"
#include "PubSubClient.h"


#define HEAT_SENSOR 18
#define SENSOR_TYPE DHT11
#define LIGHT_SENSOR 34

const char* ssid = "Ace 6";  
const char* pwd =  "12345678"; 




const char* mqtt_server = "[IP_ADDRESS]";

const int   mqtt_port   = 1885;
const char* mqtt_user   = "phungvanhai";
const char* mqtt_pass   = "b23dccn281";
const char* light_topic = "data/sensor/1";
const char* heat_topic = "data/sensor/2";
const char* humid_topic = "data/sensor/3";

unsigned long lastPublishTime = 0;

// quản lý reconnect WiFi không blocking ---
unsigned long lastWifiRetryTime = 0;
const unsigned long WIFI_RETRY_INTERVAL = 5000; // thử lại mỗi 5s

// Tạo đối tượng Wi-Fi
WiFiClient espClient;
// Tạo đối tượng MQTT, sử dụng kết nối Wi-Fi ở trên
PubSubClient mqttClient(espClient);

DHT dht(HEAT_SENSOR, SENSOR_TYPE);

struct Device {
    int id;
    int pin;
    const char* actionTopic;
    const char* statusTopic;
};

Device devices[] = {
    {1, 19, "device/1/action", "device/1/status"},
    {2, 21, "device/2/action", "device/2/status"},

};
const int NUM_DEVICES = sizeof(devices) / sizeof(devices[0]);

void respondStatus(const char* topic,int pin){
    int pinState = digitalRead(pin);
    if(pinState == HIGH) mqttClient.publish(topic, "ON", true);
    else mqttClient.publish(topic, "OFF", true);
}

void connectMQTT(){
    while(!mqttClient.connected()){
        Serial.println("Dang ket noi MQTT...");
        
        String clientId = "ESP32-" + String((uint32_t)ESP.getEfuseMac(), HEX);
        
        if(mqttClient.connect(clientId.c_str(), mqtt_user, mqtt_pass)){
            Serial.println("Ket noi MQTT thanh cong"); 
            mqttClient.subscribe("device/+/action");

            // đồng bộ lại trạng thái tất cả thiết bị thật ngay khi (re)connect MQTT
            for(int i = 0; i < NUM_DEVICES; i++){
                respondStatus(devices[i].statusTopic, devices[i].pin);
            }
        }
        else{
            Serial.print("Ket noi MQTT that bai, ma loi: ");
            Serial.println(mqttClient.state());
            Serial.println("thu lai sau 5s");
            delay(5000);
        }
    }
}

bool connectWiFi(){
    WiFi.begin(ssid, pwd);
    Serial.print("Dang ket noi WiFi");
    unsigned long startTime = millis();
    while(WiFi.status() != WL_CONNECTED && millis() - startTime < 20000){
        delay(1000);
        Serial.print(".");
    }
    Serial.println();
    if(WiFi.status() == WL_CONNECTED){
        Serial.println("Ket noi WiFi thanh cong");
        Serial.print("IP: ");
        Serial.println(WiFi.localIP());
        return true;
    }

    Serial.println("Ket noi WiFi that bai");
    return false;
}

void callback (char* topic, byte* payload, unsigned int length){
    String topicStr = String(topic);
    String message = "";
    for(int i = 0; i < length;i++)  message += (char)payload[i];
    
    Serial.println("===== MQTT COMMAND =====");
    Serial.print("Topic: ");
    Serial.println(topic);
    Serial.print("Message: ");
    Serial.println(message);

    if(topicStr == "device/all/action"){
        for(int i = 0; i < NUM_DEVICES; i++){
            
            if(message == "ON"){
                digitalWrite(devices[i].pin, HIGH);
            }
            else if(message == "OFF"){
                digitalWrite(devices[i].pin, LOW);
            }
            respondStatus(devices[i].statusTopic, devices[i].pin);
        }  
        return;
    }
    // Duyệt mảng thiết bị, tìm đúng thiết bị khớp topic 
    for(int i = 0; i < NUM_DEVICES; i++){
        if(topicStr == devices[i].actionTopic){
            if(message == "ON"){
                digitalWrite(devices[i].pin, HIGH);
            }
            else if(message == "OFF"){
                digitalWrite(devices[i].pin, LOW);
            }
            respondStatus(devices[i].statusTopic, devices[i].pin);
            break; 
        }
    }
}

void setup() {
    for(int i = 0; i < NUM_DEVICES; i++){
        pinMode(devices[i].pin, OUTPUT);
    }
    Serial.begin(115200);
    dht.begin();

    connectWiFi();

    mqttClient.setServer(mqtt_server, mqtt_port);
    mqttClient.setCallback(callback);
}

void loop() {

    //  WiFi rớt giữa chừng -> thử reconnect không blocking
    if(WiFi.status() != WL_CONNECTED){
        Serial.println("Wi-Fi da mat ket noi");
        if(millis() - lastWifiRetryTime > WIFI_RETRY_INTERVAL){
            lastWifiRetryTime = millis();
            Serial.println("Dang thu ket noi lai WiFi...");
            WiFi.reconnect();
        }
    }
    else{
        //  chỉ xử lý MQTT khi đã chắc chắn có WiFi
        if(!mqttClient.connected()) connectMQTT();

        mqttClient.loop();

        if(millis() - lastPublishTime > 2000){
            lastPublishTime = millis();
            Serial.println("Wi-Fi dang ket noi");

            float heat = dht.readTemperature();
            float humidity = dht.readHumidity();
            int light = analogRead(LIGHT_SENSOR);

            if(isnan(heat) || isnan(humidity)){
                Serial.println("Loi doc cam bien nhiet");
            }
            else{
                Serial.printf("Nhiet do: %.1f C\n", heat);
                Serial.printf("Do am: %.1f %%\n", humidity);
                mqttClient.publish(heat_topic, String(heat, 1).c_str());
                mqttClient.publish(humid_topic, String(humidity, 1).c_str());
            }
            Serial.printf("Anh sang: %d \n", light);
            mqttClient.publish(light_topic, String(light).c_str());
        }
    }
}