import paho.mqtt.client as mqtt
import time 
import sys
import random
import json
import numpy as np
from time import sleep

HOST = "192.168.1.14"

pub_topic = "DATA_FROM_AI"
sub_topic = "DATA_FROM_ANDROID"
KEYEVENT_DPAD_LEFT = 21
KEYEVENT_DPAD_RIGHT = 22
KEYEVENT_SPACE = 62
KEYEVENT_NOTHING = 0

# The callback for when the client receives a CONNACK response from the server.
def on_connect(client, userdata, flags, rc):
    print("Connected with result code " + str(rc))
    # Subscribing in on_connect() means that if we lose the connection and
    # reconnect then subscriptions will be renewed.
    client.subscribe(sub_topic)

    # reset the client
    payload = {
        'type': 'reset'
    }
    
    client.publish(pub_topic, payload=str(payload))

# The callback for when a PUBLISH message is received from the server.
def on_message(client, userdata, msg):
    print("Message received: " + str(msg.payload) + ", topic: " + msg.topic)

    m_decode=str(msg.payload.decode("utf-8","ignore"))
    json_msg = json.loads(m_decode)

    sleep(0.05)

    if json_msg['done']:
        payload = {
            'type': 'reset'
        }
    else:
        payload = {
            'type': 'step',
            'action': np.random.choice([KEYEVENT_DPAD_LEFT, KEYEVENT_DPAD_RIGHT, KEYEVENT_SPACE, KEYEVENT_NOTHING], p=[0.3, 0.3, 0.1, 0.3])
        }

    client.publish(pub_topic, payload=str(payload))

    # client.publish(pub_topic, payload=random.choice([KEYEVENT_DPAD_LEFT, KEYEVENT_DPAD_RIGHT, KEYEVENT_SPACE]))

client = mqtt.Client("LunarLanderAI")
client.on_connect = on_connect
client.on_message = on_message

client.connect(HOST, port=1883, keepalive=60)

client.loop_forever()
