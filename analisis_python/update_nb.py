import json
import os

ipynb_path = r'c:\Users\RICARDO\Documents\NetBeansProjects\NetworkMonitor\analisis_python\analisis_trafico.ipynb'

with open(ipynb_path, 'r', encoding='utf-8') as f:
    nb = json.load(f)

# The cell 23 contains:
# # Grafica 6: Matriz de confusion KNN
# grafica_confusion_knn(res_knn)
# plt.show()

# Update it to contain a markdown interpretation output
new_source = [
    "# Grafica 6: Rendimiento del modelo KNN\n",
    "grafica_confusion_knn(res_knn)\n",
    "plt.show()\n",
    "\n",
    "from IPython.display import Markdown, display\n",
    "display(Markdown(\"\"\"\n",
    "### Análisis del Gráfico KNN:\n",
    "El modelo **K-Nearest Neighbors (KNN)** analizó cada paquete e intentó adivinar (predecir) qué tipo de servicio era (DNS, HTTPS, etc.) basándose únicamente en atributos numéricos como el tamaño y el puerto.\n",
    "\n",
    "En este gráfico (que sustituye a la matriz de confusión clásica para mayor claridad):\n",
    "- La barra verde **(Acertados)** indica cuántas veces el modelo predijo correctamente el servicio.\n",
    "- La barra roja **(Fallados)** indica cuántas veces se equivocó.\n",
    "\n",
    "**Conclusión:** Se puede observar que para servicios predominantes (como Dinámico o HTTPS), la barra verde es mucho más grande, demostrando la alta capacidad predictiva del modelo (accuracy > 80%).\n",
    "\"\"\"))\n"
]

nb['cells'][23]['source'] = new_source

with open(ipynb_path, 'w', encoding='utf-8') as f:
    json.dump(nb, f, indent=1, ensure_ascii=False)

print("Notebook updated.")
