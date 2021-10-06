#!/usr/bin/env python
# coding: utf-8

# In[1]:


import os
import time
import numpy as np
import shutil
import cv2
import pickle
import itertools

import matplotlib
import matplotlib.pyplot as plt

import tensorflow as tf

from tqdm import tqdm


# In[2]:


import mediapipe as mp


# In[3]:


hands = mp.solutions.hands.Hands(
    static_image_mode=False,
    max_num_hands=1,
    min_detection_confidence=0.5)

drawing = mp.solutions.drawing_utils


# In[4]:


def frame_extract(path):
    vcap = cv2.VideoCapture(path)
    while True:
        ret, frame = vcap.read()
        if not ret:
            break
            
        height, width = frame.shape[:2]
        img = frame
        img = cv2.resize(frame, (640, int(height * (640 / width))))
        img = img[:, :, ::-1]
        yield img


# In[5]:


def process(image, preview=False):
    if preview:
        preview_image = image.copy()
        
    outputs = hands.process(image)
    
    shape = (len(mp.solutions.hands.HandLandmark), 2)
    
    multi_coords = []
    if outputs.multi_hand_landmarks:
        for landmarks in outputs.multi_hand_landmarks:
            coords = np.zeros(shape)
            if preview:
                drawing.draw_landmarks(preview_image, landmarks, mp.solutions.hands.HAND_CONNECTIONS)
                
            for landmark_type in mp.solutions.hands.HandLandmark:
                landmark = landmarks.landmark[landmark_type]
                coords[landmark_type] = (landmark.x, landmark.y)
                
            multi_coords.append(coords)
            
    if preview:
        return multi_coords, preview_image
    
    return multi_coords


# In[6]:


def generate(image, coords, keep_scale=True):
    coords = coords * image.shape[1::-1]
    min_x, min_y = np.amin(coords, axis=0)
    max_x, max_y = np.amax(coords, axis=0)
    
    if keep_scale:
        ratio = max(max_x - min_x, max_y - min_y)
    else:
        ratio = (max_x - min_x, max_y - min_y)
    coords = (coords - (min_x, min_y)) / ratio
    
    w, h = 32, 32
    image = np.zeros((h, w, 1), dtype=np.uint8)
    for x, y in coords:
        image[round(y * (h - 1)), round(x * (w - 1))] = 255
    
    return image, coords


# In[7]:


def save(path, image):
    cv2.imwrite(path, image[..., ::-1])
    
def mkdir(path, clear=True):
    if not os.path.isdir(path):
        os.mkdir(path)
    elif clear:
        shutil.rmtree(path)
        os.mkdir(path)
        
def save_pkl(path, obj):
    with open(path, 'wb') as file:
        pickle.dump(obj, file)
        
def load_pkl(path):
    with open(path, 'rb') as file:
        obj = pickle.load(file)
    return obj
        
def save_npy(path, arr):
    with open(path, 'wb') as file:
        np.save(file, arr)
        
def load_npy(path):
    with open(path, 'rb') as file:
        arr = np.load(file)
    return arr


# In[8]:


def process_video(class_name, video_name, verbose=False, warm_up=True):
    video_path = os.path.join('data', 'videos', class_name, video_name)
    class_dir = os.path.join('data', 'frames', class_name)
    frame_dir = os.path.join(class_dir, os.path.splitext(video_name)[0])
    preview_dir = os.path.join(frame_dir, 'preview')
    coords_dir = os.path.join(frame_dir, 'coords')

    if os.path.isdir(frame_dir):
        return video_path, frame_dir
    
    mkdir(class_dir, clear=False)
    mkdir(frame_dir)
    mkdir(preview_dir)
    mkdir(coords_dir)

    if verbose:
        print(video_path, frame_dir)

    landmarks = []
    
    if warm_up:
        for frame in frame_extract(video_path):
            hands.process(frame)
            
        frame_0, frame_1 = itertools.islice(frame_extract(video_path), 2)
        for _ in range(50):
            hands.process(frame_0)
            hands.process(frame_1)

    for i, frame in enumerate(frame_extract(video_path)):
        multi_coords, preview_image = process(frame, preview=True)

        preview_path = os.path.join(preview_dir, f'{i}.jpg')
        save(preview_path, preview_image)

        if verbose:
            if i == 0:
                plt.imshow(preview_image)
                plt.show()

        normalized_coords = []
        for j, coords in enumerate(multi_coords):
            image, coords = generate(frame, coords)
            normalized_coords.append(coords)

            coords_path = os.path.join(coords_dir, f'{i}_{j}.bmp')
            save(coords_path, image)

            if verbose:
                if i == 0:
                    # plt.imshow(image, cmap='gray')
                    # plt.show()

                    print(coords.shape)
                    print(coords)

        landmarks.append(normalized_coords)

    landmarks_path = os.path.join(frame_dir, 'landmarks.pkl')
    save_pkl(landmarks_path, landmarks)

    data = np.zeros((len(landmarks), 21, 2))
    for i, multi_coords in enumerate(landmarks):
        if len(multi_coords) > 0:
            data[i] = multi_coords[0]
    data_path = os.path.join(frame_dir, 'data.npy')
    save_npy(data_path, data)
    
    return video_path, frame_dir


# In[9]:


videos_dir = os.path.join('data', 'videos')
for class_name in os.listdir(videos_dir):
    class_dir = os.path.join(videos_dir, class_name)
    if not os.path.isdir(class_dir):
        continue
    print(class_name)
    for video_name in os.listdir(class_dir):
        if not os.path.splitext(video_name)[1] == '.mp4':
            continue
        video_path, frame_dir = process_video(class_name, video_name, verbose=False)
        print('-', video_path, frame_dir)


# In[10]:


if False:
    class_name = 'point'
    video_name = '20210819_100907.mp4'
    video_path, frame_dir = process_video(class_name, video_name, verbose=True)
    
    print(load_npy(os.path.join(frame_dir, 'data.npy'))[0])


# In[11]:


def flip_y_fn(coords):
    flipped = np.copy(coords)
    flipped[..., 0] = np.amax(coords[..., 0]) - coords[..., 0]
    return flipped


# In[12]:


coords = load_npy(os.path.join('data/frames/point/20210819_100850', 'data.npy'))[0]

print(coords.shape)
#print(coords)
#print(flip_y(coords))

w, h = 32, 32
image = np.zeros((h, w, 1), dtype=np.uint8)
for x, y in coords:
    image[round(y * (h - 1)), round(x * (w - 1))] = 255
    
# plt.imshow(image, cmap='gray')
# plt.show()

coords = flip_y_fn(coords)

w, h = 32, 32
image = np.zeros((h, w, 1), dtype=np.uint8)
for x, y in coords:
    image[round(y * (h - 1)), round(x * (w - 1))] = 255
    
# plt.imshow(image, cmap='gray')
# plt.show()


# In[13]:


def load_data():
    data = {}
    data_dir = os.path.join('data', 'frames')
    for class_name in os.listdir(data_dir):
        class_dir = os.path.join(data_dir, class_name)
        if not os.path.isdir(class_dir):
            continue
        print(class_name)
        items = []
        for video_name in os.listdir(class_dir):
            video_dir = os.path.join(class_dir, video_name)
            if not os.path.isdir(video_dir):
                continue
            data_path = os.path.join(video_dir, 'data.npy')
            item = load_npy(data_path)
            items.append(item)
            print(item.shape)
        data[class_name] = items
        
    return data
            
data = load_data()


# In[14]:


len(data['point'])


# In[15]:


def sliding_window(sequence, timesteps=10, strides=1):
    n = len(sequence)
    steps = (n - timesteps) // strides + 1
    # print(n, steps)
    for i in range(steps):
        start = i * strides
        end = start + timesteps
        # print(start, end)
        yield sequence[start:end]


# In[16]:


for seq in sliding_window(range(20), timesteps=10, strides=1):
    print(seq)
#     print(seq)


# In[17]:


def from_sequences(sequences, labels, flip_y=True):
    for sequence in sequences:
        for features in sliding_window(sequence):
            #print(features.shape)
            yield tf.expand_dims(features, axis=1), labels
            if flip_y:
                #print(flip_y_fn(features))
                yield tf.expand_dims(flip_y_fn(features), axis=1), labels


# In[18]:


classes = ['cursor', 'fist', 'grab', 'negative', 'palm', 'point', 'thumb']
n_classes = len(classes)
encoding = tf.one_hot(range(n_classes), n_classes)
print(encoding)

def label_encoding(class_name):
    return encoding[classes.index(class_name)]

input_shape = (10, 1, 21, 2)
output_shape = (n_classes,)

print(input_shape, output_shape)


# In[19]:


label_encoding('palm')


# In[20]:


def dataset_from_class(class_name):
    def gen():
        yield from from_sequences(data[class_name], label_encoding(class_name))

    dataset = tf.data.Dataset.from_generator(
        gen, 
        output_signature=(
            tf.TensorSpec(shape=input_shape), 
            tf.TensorSpec(shape=output_shape, )))
    
    return dataset


# In[21]:


dataset_classes = []
for class_name in classes:
    dataset = dataset_from_class(class_name)
    print(class_name, len(list(dataset)))
    dataset_classes.append(dataset)


# In[22]:


full_dataset = tf.data.experimental.sample_from_datasets(dataset_classes)
print(len(list(full_dataset)))


# In[23]:


# sampled_dataset = tf.data.experimental.sample_from_datasets(
#     [dataset_palm.repeat(), dataset.repeat()])

sampled_dataset = tf.data.experimental.sample_from_datasets(
    [dataset.repeat() for dataset in dataset_classes])

# sampled_dataset = tf.data.experimental.sample_from_datasets(dataset_classes)

total = tf.zeros((7,))
for features, labels in sampled_dataset.shuffle(100).take(10000):
    print(labels)
    total += labels


# In[24]:


total


# In[25]:


for features, labels in sampled_dataset.take(2):
    print(labels)
    for coords in features:
        coords = np.asarray(coords[0])
        w, h = 32, 32
        image = np.zeros((h, w, 1), dtype=np.uint8)
        for x, y in coords:
            image[round(y * (h - 1)), round(x * (w - 1))] = 255

        # plt.imshow(image, cmap='gray')
        # plt.show()
        
    print(features.shape, labels.shape)


# In[26]:


from tensorflow.keras import Input, Model
from tensorflow.keras.layers import ConvLSTM2D, Flatten, Dense, Dropout
from tensorflow.keras.optimizers import Adam


# In[27]:


def neural_net(input_shape, output_shape):
    inputs = Input(input_shape)
    x = inputs
    x = ConvLSTM2D(32, (1, 3), activation='relu')(x)
    x = Flatten()(x)
    x = Dropout(0.5)(x)
    x = Dense(100, activation='relu')(x)
    x = Dense(output_shape[0], activation='softmax')(x)
    outputs = x
    
    return Model(inputs, outputs)


# In[28]:


model = neural_net((10, 1, 21, 2), output_shape)
model.compile(
    optimizer=Adam(lr=1e-3), 
    loss='categorical_crossentropy', 
    metrics=['accuracy'])
model.summary()


# In[29]:


if os.path.isfile('latest.h5'):
    model = tf.keras.models.load_model('latest.h5')
else:
    model.fit(
        sampled_dataset.shuffle(10000).batch(32).take(1000), 
        epochs=5)


# In[30]:


model.evaluate(full_dataset.batch(32))


# In[31]:


model.predict(sampled_dataset.shuffle(1000).batch(1).take(1))


# In[32]:


for features, labels in sampled_dataset.shuffle(10000).take(100):
    print('truth', tf.argmax(labels))
    print('predicted', tf.argmax(model.predict(tf.convert_to_tensor([features]))[0]))


# In[33]:


model.save('latest.h5')


# In[34]:


if True:
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS, tf.lite.OpsSet.SELECT_TF_OPS]
    tflite_model = converter.convert()
    
    with open('latest.tflite', 'wb') as f:
        f.write(tflite_model)


# In[35]:


# with open('latest.tflite', 'rb') as fid:
#     tflite_model = fid.read()
    
# interpreter = tf.lite.Interpreter(model_content=tflite_model)
# interpreter.allocate_tensors()

# input_index = interpreter.get_input_details()[0]["index"]
# output_index = interpreter.get_output_details()[0]["index"]

# # Gather results for the randomly sampled test images
# predictions = []

# test_labels, test_imgs = [], []
# for features, label in tqdm(sampled_dataset.shuffle(100).take(10)):
#     interpreter.set_tensor(input_index, tf.convert_to_tensor([features]))
#     interpreter.invoke()
#     predictions.append(interpreter.get_tensor(output_index))
    
#     test_labels.append(label.numpy()[0])
#     test_imgs.append(features)


# In[36]:


with open('latest.tflite', 'rb') as fid:
    tflite_model = fid.read()
    
interpreter = tf.lite.Interpreter(model_content=tflite_model)
interpreter.allocate_tensors()

print(interpreter.get_input_details())


# In[42]:


features, label = list(sampled_dataset.take(1))[0]
features = tf.expand_dims(features, axis=0)


# In[45]:


interpreter.set_tensor(interpreter.get_input_details()[0]['index'], features)


# In[ ]:

try:
    interpreter.invoke()
except RuntimeError as e:
    print(e)


# In[ ]:


print(interpreter.get_tensor(interpreter.get_output_details()[0]['index']))

