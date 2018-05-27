import paho.mqtt.client as mqtt
import time 
import sys
import random

pub_topic = "DATA_FROM_AI"
sub_topic = "DATA_FROM_ANDROID"
KEYEVENT_DPAD_LEFT = 21
KEYEVENT_DPAD_RIGHT = 22
KEYEVENT_SPACE = 62

# The callback for when the client receives a CONNACK response from the server.
def on_connect(client, userdata, flags, rc):
    print("Connected with result code " + str(rc))
    # Subscribing in on_connect() means that if we lose the connection and
    # reconnect then subscriptions will be renewed.
    client.subscribe(sub_topic)

# The callback for when a PUBLISH message is received from the server.
def on_message(client, userdata, msg):
    print("Message received: " + str(msg.payload) + ", topic: " + msg.topic)
    client.publish(pub_topic, payload=random.choice([KEYEVENT_DPAD_LEFT, KEYEVENT_DPAD_RIGHT, KEYEVENT_SPACE]))

client = mqtt.Client("LunarLanderAI")
client.on_connect = on_connect
client.on_message = on_message

client.connect("localhost", port=1883, keepalive=60)

client.loop_forever()
