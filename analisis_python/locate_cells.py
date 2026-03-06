import json
import os

ipynb_path = r'c:\Users\RICARDO\Documents\NetBeansProjects\NetworkMonitor\analisis_python\analisis_trafico.ipynb'

with open(ipynb_path, 'r', encoding='utf-8') as f:
    nb = json.load(f)

print("--- Cell 22 ---")
print(''.join(nb['cells'][22]['source']))
print("--- Cell 23 ---")
print(''.join(nb['cells'][23]['source']))

print("--- Cell 26 ---")
print(''.join(nb['cells'][26]['source']))
print("--- Cell 27 ---")
print(''.join(nb['cells'][27]['source']))
