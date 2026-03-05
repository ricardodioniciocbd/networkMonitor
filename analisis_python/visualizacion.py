"""
visualizacion.py
Genera todas las gráficas de la práctica y las guarda en resultados/.
"""

import os
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
import seaborn as sns

RESULTADOS_DIR = os.path.join(os.path.dirname(__file__), "resultados")
os.makedirs(RESULTADOS_DIR, exist_ok=True)

sns.set_theme(style="whitegrid", palette="muted")


def _guardar(nombre: str):
    ruta = os.path.join(RESULTADOS_DIR, nombre)
    plt.savefig(ruta, dpi=150, bbox_inches="tight")
    print(f"  Gráfica guardada: {ruta}")
    plt.close()


# ---------------------------------------------------------------------------
# 1. TCP vs UDP
# ---------------------------------------------------------------------------
def grafica_tcp_udp(df: pd.DataFrame):
    conteo = df["protocolo"].value_counts()

    fig, axes = plt.subplots(1, 2, figsize=(12, 5))
    fig.suptitle("Distribución de Protocolos TCP vs UDP", fontsize=14, fontweight="bold")

    colores = {"TCP": "#2196F3", "UDP": "#FF9800"}
    barras = [colores.get(p, "#9E9E9E") for p in conteo.index]

    axes[0].bar(conteo.index, conteo.values, color=barras, edgecolor="white", linewidth=1.2)
    axes[0].set_title("Conteo de paquetes por protocolo")
    axes[0].set_xlabel("Protocolo")
    axes[0].set_ylabel("Número de paquetes")
    for i, v in enumerate(conteo.values):
        axes[0].text(i, v + conteo.values.max() * 0.01, str(v), ha="center", fontsize=10)

    axes[1].pie(
        conteo.values,
        labels=conteo.index,
        autopct="%1.1f%%",
        colors=barras,
        startangle=90,
        wedgeprops={"edgecolor": "white", "linewidth": 2},
    )
    axes[1].set_title("Proporción TCP / UDP")

    plt.tight_layout()
    _guardar("01_tcp_vs_udp.png")


# ---------------------------------------------------------------------------
# 2. Tamaño de paquetes vs hora del día (dispersión)
# ---------------------------------------------------------------------------
def grafica_dispersion_tamano_hora(df: pd.DataFrame):
    fig, ax = plt.subplots(figsize=(12, 5))

    colores = {"TCP": "#2196F3", "UDP": "#FF9800"}
    for proto, grp in df.groupby("protocolo"):
        ax.scatter(
            grp["hora"],
            grp["longitud"],
            alpha=0.3,
            s=15,
            label=proto,
            color=colores.get(proto, "#9E9E9E"),
        )

    ax.set_title("Tamaño de paquetes vs Hora del día", fontsize=13, fontweight="bold")
    ax.set_xlabel("Hora del día (0–23)")
    ax.set_ylabel("Longitud del paquete (bytes)")
    ax.set_xticks(range(0, 24))
    ax.legend(title="Protocolo")
    plt.tight_layout()
    _guardar("02_dispersion_tamano_hora.png")


# ---------------------------------------------------------------------------
# 3. Top puertos más usados
# ---------------------------------------------------------------------------
def grafica_puertos_frecuentes(df: pd.DataFrame, top_n: int = 15):
    top = df["puerto_destino"].value_counts().head(top_n)

    fig, ax = plt.subplots(figsize=(12, 6))
    barras = ax.barh(
        [str(int(p)) for p in top.index],
        top.values,
        color=sns.color_palette("Blues_d", len(top)),
        edgecolor="white",
    )
    ax.invert_yaxis()
    ax.set_title(f"Top {top_n} puertos destino más utilizados", fontsize=13, fontweight="bold")
    ax.set_xlabel("Número de paquetes")
    ax.set_ylabel("Puerto destino")

    for bar, val in zip(barras, top.values):
        ax.text(val + top.values.max() * 0.005, bar.get_y() + bar.get_height() / 2,
                str(val), va="center", fontsize=9)
    plt.tight_layout()
    _guardar("03_puertos_frecuentes.png")


# ---------------------------------------------------------------------------
# 4. Clusters de equipos (scatter 2D por PCA)
# ---------------------------------------------------------------------------
def grafica_clusters(datos_equipos: pd.DataFrame):
    niveles = datos_equipos["nivel_uso"].unique()
    paleta = {"Intensivo": "#F44336", "Moderado": "#FF9800", "Ligero": "#4CAF50"}
    colores = [paleta.get(n, "#9E9E9E") for n in datos_equipos["nivel_uso"]]

    fig, ax = plt.subplots(figsize=(10, 7))
    sc = ax.scatter(
        datos_equipos["pca_x"],
        datos_equipos["pca_y"],
        c=colores,
        s=datos_equipos["total_paquetes"] / datos_equipos["total_paquetes"].max() * 400 + 30,
        alpha=0.8,
        edgecolors="white",
        linewidth=0.8,
    )

    for _, row in datos_equipos.iterrows():
        ax.annotate(
            row["ip_origen"],
            (row["pca_x"], row["pca_y"]),
            fontsize=7,
            alpha=0.7,
            xytext=(4, 4),
            textcoords="offset points",
        )

    parches = [mpatches.Patch(color=v, label=k) for k, v in paleta.items() if k in datos_equipos["nivel_uso"].values]
    ax.legend(handles=parches, title="Nivel de uso", loc="best")
    ax.set_title("Clustering K-Means de equipos (PCA 2D)\nTamaño del punto proporcional al número de paquetes",
                 fontsize=12, fontweight="bold")
    ax.set_xlabel("Componente Principal 1")
    ax.set_ylabel("Componente Principal 2")
    plt.tight_layout()
    _guardar("04_clusters_equipos.png")


# ---------------------------------------------------------------------------
# 5. Tráfico por hora del día
# ---------------------------------------------------------------------------
def grafica_trafico_por_hora(df: pd.DataFrame):
    por_hora = df.groupby(["hora", "protocolo"]).size().unstack(fill_value=0)

    fig, ax = plt.subplots(figsize=(13, 5))
    colores = {"TCP": "#2196F3", "UDP": "#FF9800"}
    for proto in por_hora.columns:
        ax.plot(
            por_hora.index,
            por_hora[proto],
            marker="o",
            linewidth=2,
            markersize=5,
            label=proto,
            color=colores.get(proto, "#9E9E9E"),
        )

    ax.fill_between(por_hora.index, por_hora.sum(axis=1), alpha=0.08, color="grey")
    ax.set_title("Volumen de tráfico por hora del día", fontsize=13, fontweight="bold")
    ax.set_xlabel("Hora del día (0–23)")
    ax.set_ylabel("Número de paquetes")
    ax.set_xticks(range(0, 24))
    ax.legend(title="Protocolo")
    plt.tight_layout()
    _guardar("05_trafico_por_hora.png")


# ---------------------------------------------------------------------------
# 6. Matriz de confusión del KNN
# ---------------------------------------------------------------------------
def grafica_confusion_knn(res_knn: dict):
    nombres = res_knn["clases_nombres"]
    cm = res_knn["confusion_matrix"]

    fig, ax = plt.subplots(figsize=(max(8, len(nombres)), max(6, len(nombres))))
    sns.heatmap(
        cm,
        annot=True,
        fmt="d",
        cmap="Blues",
        xticklabels=nombres,
        yticklabels=nombres,
        linewidths=0.5,
        ax=ax,
    )
    ax.set_xlabel("Predicho", fontsize=11)
    ax.set_ylabel("Real", fontsize=11)
    plt.setp(ax.get_xticklabels(), rotation=45, ha="right")
    plt.setp(ax.get_yticklabels(), rotation=0)
    ax.set_title(f"Matriz de confusión – KNN (accuracy={res_knn['accuracy']:.2%})",
                 fontsize=12, fontweight="bold")
    plt.tight_layout()
    _guardar("06_confusion_knn.png")


# ---------------------------------------------------------------------------
# 7. Probabilidad de protocolo por hora (análisis predictivo)
# ---------------------------------------------------------------------------
def grafica_prediccion_hora(res_pred: dict):
    pred_df = res_pred["prediccion_por_hora"]

    fig, ax = plt.subplots(figsize=(13, 5))
    ax.fill_between(pred_df["hora"], pred_df["prob_tcp"], alpha=0.35, color="#2196F3", label="P(TCP)")
    ax.fill_between(pred_df["hora"], pred_df["prob_udp"], alpha=0.35, color="#FF9800", label="P(UDP)")
    ax.plot(pred_df["hora"], pred_df["prob_tcp"], color="#2196F3", linewidth=2)
    ax.plot(pred_df["hora"], pred_df["prob_udp"], color="#FF9800", linewidth=2)

    ax.set_title("Predicción de protocolo predominante por hora del día\n(Regresión Logística)",
                 fontsize=12, fontweight="bold")
    ax.set_xlabel("Hora del día (0–23)")
    ax.set_ylabel("Probabilidad")
    ax.set_xticks(range(0, 24))
    ax.set_ylim(0, 1)
    ax.legend(title="Protocolo")
    plt.tight_layout()
    _guardar("07_prediccion_protocolo_hora.png")


# ---------------------------------------------------------------------------
# 8. Categorías de servicio más frecuentes
# ---------------------------------------------------------------------------
def grafica_servicios(df: pd.DataFrame, top_n: int = 12):
    top = df["servicio"].value_counts().head(top_n)

    fig, ax = plt.subplots(figsize=(12, 6))
    colores = sns.color_palette("Set2", len(top))
    bars = ax.bar(top.index, top.values, color=colores, edgecolor="white")
    ax.set_title(f"Top {top_n} servicios/categorías más frecuentes", fontsize=13, fontweight="bold")
    ax.set_xlabel("Servicio / Categoría de puerto")
    ax.set_ylabel("Número de paquetes")
    ax.tick_params(axis="x", rotation=35)
    for bar, val in zip(bars, top.values):
        ax.text(bar.get_x() + bar.get_width() / 2, val + top.values.max() * 0.01,
                str(val), ha="center", fontsize=9)
    plt.tight_layout()
    _guardar("08_servicios_frecuentes.png")


# ---------------------------------------------------------------------------
# 9. Aplicaciones/servicios inferidos por IP (heurística)
# ---------------------------------------------------------------------------
def grafica_apps_inferidas(df: pd.DataFrame):
    if "app_inferida" not in df.columns:
        print("  [skip] columna 'app_inferida' no disponible.")
        return

    conteo = df["app_inferida"].value_counts()

    fig, axes = plt.subplots(1, 2, figsize=(14, 6))
    fig.suptitle(
        "Aplicaciones/Servicios inferidos por IP destino\n"
        "(Aproximación por prefijo de IP — ChatGPT, YouTube, Facebook, etc.)",
        fontsize=12, fontweight="bold"
    )

    colores = sns.color_palette("Set3", len(conteo))

    axes[0].barh(conteo.index[::-1], conteo.values[::-1], color=colores[::-1], edgecolor="white")
    axes[0].set_title("Conteo de paquetes por app inferida")
    axes[0].set_xlabel("Número de paquetes")

    axes[1].pie(
        conteo.values,
        labels=conteo.index,
        autopct="%1.1f%%",
        colors=colores,
        startangle=90,
        wedgeprops={"edgecolor": "white", "linewidth": 1.5},
    )
    axes[1].set_title("Proporción por app")

    plt.tight_layout()
    _guardar("09_apps_inferidas.png")


# ---------------------------------------------------------------------------
# Función principal: genera todas las gráficas
# ---------------------------------------------------------------------------
def generar_todas(df: pd.DataFrame, res_knn: dict, res_cluster: dict, res_pred: dict):
    print("\n--- Generando gráficas ---")
    grafica_tcp_udp(df)
    grafica_dispersion_tamano_hora(df)
    grafica_puertos_frecuentes(df)
    grafica_clusters(res_cluster["datos_equipos"])
    grafica_trafico_por_hora(df)
    grafica_confusion_knn(res_knn)
    grafica_prediccion_hora(res_pred)
    grafica_servicios(df)
    grafica_apps_inferidas(df)
    print("--- Todas las gráficas generadas ---\n")


if __name__ == "__main__":
    from limpieza_datos import cargar_datos
    from modelos import clasificar_tipo_trafico, clustering_equipos, predecir_protocolo_por_hora

    df = cargar_datos(verbose=False)
    res_knn     = clasificar_tipo_trafico(df)
    res_cluster = clustering_equipos(df)
    res_pred    = predecir_protocolo_por_hora(df)
    generar_todas(df, res_knn, res_cluster, res_pred)
