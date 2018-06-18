import paho.mqtt.client as mqtt
import time 
import sys
import random
import json
from time import sleep
from threading import Event, Thread

pub_topic = "DATA_FROM_AI"
sub_topic = "DATA_FROM_ANDROID"
KEYEVENT_DPAD_LEFT = 21
KEYEVENT_DPAD_RIGHT = 22
KEYEVENT_SPACE = 62

DELAY = 0.1

def sent_reset_msg():
    payload = {
        'type': "RESET_REQUEST"
    }
    print("Message published: " + str(payload))
    client.publish(pub_topic, payload=str(payload))


def sent_step_msg():
    payload = {
        'type': "STEP_REQUEST",
        'action': random.choice([KEYEVENT_DPAD_LEFT, KEYEVENT_DPAD_RIGHT])
    }

    print("Message published: " + str(payload))
    client.publish(pub_topic, payload=str(payload))


# The callback for when the client receives a CONNACK response from the server.
def on_connect(client, userdata, flags, rc):
    print("Connected with result code " + str(rc))
    # Subscribing in on_connect() means that if we lose the connection and
    # reconnect then subscriptions will be renewed.
    client.subscribe(sub_topic)
    sent_reset_msg()

# The callback for when a PUBLISH message is received from the server.
def on_message(client, userdata, msg):
    print("Message received: " + str(msg.payload) + ", topic: " + msg.topic)
    
    m_decode=str(msg.payload.decode("utf-8","ignore"))
    lunar_message = json.loads(m_decode)

    sleep(DELAY)

    if (lunar_message["done"]):
        sent_reset_msg()
    else:
        sent_step_msg()


client = mqtt.Client("LunarLanderAI")
client.on_connect = on_connect
client.on_message = on_message

client.connect("127.0.0.1", port=1883, keepalive=60)

client.loop_forever()
