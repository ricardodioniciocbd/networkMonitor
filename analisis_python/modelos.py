"""
modelos.py
Implementa los tres modelos de Data Science pedidos en la práctica:
  1. KNN (clasificación de tipo de tráfico)
  2. K-Means (clustering de equipos por nivel de uso)
  3. Análisis predictivo (predicción de protocolo por hora del día)
"""

import numpy as np
import pandas as pd
from sklearn.neighbors import KNeighborsClassifier
from sklearn.cluster import KMeans
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import train_test_split
from sklearn.metrics import (
    accuracy_score, classification_report, confusion_matrix, silhouette_score
)
from sklearn.preprocessing import StandardScaler
from sklearn.decomposition import PCA


# ---------------------------------------------------------------------------
# 1. CLASIFICACIÓN KNN
# ---------------------------------------------------------------------------

def clasificar_tipo_trafico(df: pd.DataFrame, k: int = 5, test_size: float = 0.2):
    """
    Entrena un clasificador KNN para identificar el tipo de tráfico
    (servicio de red) a partir de características del paquete.

    Parámetros
    ----------
    df : DataFrame limpio devuelto por limpieza_datos.cargar_datos()
    k  : número de vecinos del KNN
    test_size : fracción de datos para prueba

    Devuelve
    -------
    dict con modelo, métricas y datos de test.
    """
    # Etiqueta: servicio (calculado en limpieza_datos.py)
    X = df[["longitud_scaled", "po_scaled", "pd_scaled", "protocolo_enc"]].values
    y = df["servicio_enc"].values

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=test_size, random_state=42, stratify=y
    )

    knn = KNeighborsClassifier(n_neighbors=k)
    knn.fit(X_train, y_train)

    y_pred = knn.predict(X_test)
    acc = accuracy_score(y_test, y_pred)

    # Decodificar etiquetas para el reporte
    servicios_unicos = df["servicio"].unique()
    le_clases = sorted(df["servicio_enc"].unique())

    print("=" * 60)
    print(f"  MODELO KNN  (k={k})")
    print("=" * 60)
    print(f"  Registros en train : {len(X_train)}")
    print(f"  Registros en test  : {len(X_test)}")
    print(f"  Accuracy            : {acc:.4f} ({acc*100:.2f}%)")
    print()
    print(classification_report(
        y_test, y_pred,
        labels=le_clases,
        target_names=[df[df["servicio_enc"] == c]["servicio"].iloc[0] for c in le_clases],
        zero_division=0
    ))

    return {
        "modelo": knn,
        "accuracy": acc,
        "y_test": y_test,
        "y_pred": y_pred,
        "X_test": X_test,
        "confusion_matrix": confusion_matrix(y_test, y_pred),
        "clases": le_clases,
        "clases_nombres": [df[df["servicio_enc"] == c]["servicio"].iloc[0] for c in le_clases],
    }


# ---------------------------------------------------------------------------
# 2. CLUSTERING K-MEANS
# ---------------------------------------------------------------------------

def _agregar_por_ip(df: pd.DataFrame) -> pd.DataFrame:
    """
    Agrega métricas por IP de origen para caracterizar el comportamiento
    de cada equipo en la red.
    """
    agg = df.groupby("ip_origen").agg(
        total_paquetes=("longitud", "count"),
        bytes_total=("longitud", "sum"),
        bytes_promedio=("longitud", "mean"),
        puertos_unicos=("puerto_destino", "nunique"),
        pct_tcp=("protocolo", lambda s: (s == "TCP").mean()),
    ).reset_index()
    return agg


def clustering_equipos(df: pd.DataFrame, k: int = 3):
    """
    Agrupa los equipos (IPs de origen) según su comportamiento de red
    usando K-Means.

    Parámetros
    ----------
    df : DataFrame limpio
    k  : número de clusters (3 por defecto: intensivo, moderado, ligero)

    Devuelve
    -------
    dict con modelo, DataFrame de equipos con cluster asignado y métricas.
    """
    agg = _agregar_por_ip(df)

    features = ["total_paquetes", "bytes_total", "bytes_promedio", "puertos_unicos", "pct_tcp"]
    X = agg[features].fillna(0).values

    scaler = StandardScaler()
    X_scaled = scaler.fit_transform(X)

    kmeans = KMeans(n_clusters=k, random_state=42, n_init=10)
    agg["cluster"] = kmeans.fit_predict(X_scaled)

    sil = silhouette_score(X_scaled, agg["cluster"]) if k > 1 else float("nan")

    # Etiquetar clusters según volumen de tráfico (mayor bytes_total → cluster más intensivo)
    medias = agg.groupby("cluster")["bytes_total"].mean().sort_values(ascending=False)
    etiquetas = {c: lbl for c, lbl in zip(medias.index, ["Intensivo", "Moderado", "Ligero"][:k])}
    agg["nivel_uso"] = agg["cluster"].map(etiquetas)

    # Reducir a 2D para visualización
    pca = PCA(n_components=2, random_state=42)
    coords = pca.fit_transform(X_scaled)
    agg["pca_x"] = coords[:, 0]
    agg["pca_y"] = coords[:, 1]

    print("=" * 60)
    print(f"  CLUSTERING K-MEANS  (k={k})")
    print("=" * 60)
    print(f"  Equipos analizados  : {len(agg)}")
    print(f"  Silhouette score    : {sil:.4f}")
    print()
    print(agg.groupby("nivel_uso")[features].mean().round(2).to_string())
    print()
    print(agg[["ip_origen", "total_paquetes", "bytes_total", "nivel_uso"]].to_string(index=False))

    return {
        "modelo": kmeans,
        "datos_equipos": agg,
        "silhouette": sil,
        "features": features,
        "etiquetas": etiquetas,
    }


# ---------------------------------------------------------------------------
# 3. ANÁLISIS PREDICTIVO
# ---------------------------------------------------------------------------

def predecir_protocolo_por_hora(df: pd.DataFrame):
    """
    Predice el protocolo predominante (TCP/UDP) en función de la hora del día
    usando regresión logística.

    Parámetros
    ----------
    df : DataFrame limpio con columna 'hora' (0-23)

    Devuelve
    -------
    dict con modelo, métricas y predicciones por hora.
    """
    # Características: hora y longitud promedio en esa hora
    X = df[["hora", "longitud_scaled"]].values
    y = df["protocolo_enc"].values   # 0=UDP, 1=TCP (LabelEncoder orden alfabético)

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42
    )

    modelo = LogisticRegression(max_iter=500, random_state=42)
    modelo.fit(X_train, y_train)

    y_pred = modelo.predict(X_test)
    acc = accuracy_score(y_test, y_pred)

    # Predicción para cada hora del día (0-23), asumiendo longitud_scaled = 0
    horas = np.arange(0, 24).reshape(-1, 1)
    horas_input = np.hstack([horas, np.zeros((24, 1))])
    pred_hora = modelo.predict(horas_input)
    prob_hora = modelo.predict_proba(horas_input)

    pred_df = pd.DataFrame({
        "hora": range(24),
        "protocolo_pred": ["TCP" if p == 1 else "UDP" for p in pred_hora],
        "prob_tcp": prob_hora[:, 1],
        "prob_udp": prob_hora[:, 0],
    })

    print("=" * 60)
    print("  ANÁLISIS PREDICTIVO  (Regresión Logística)")
    print("=" * 60)
    print(f"  Accuracy : {acc:.4f} ({acc*100:.2f}%)")
    print()
    print(classification_report(y_test, y_pred, target_names=["UDP", "TCP"], zero_division=0))
    print("\n  Predicción de protocolo por hora del día:")
    print(pred_df.to_string(index=False))

    return {
        "modelo": modelo,
        "accuracy": acc,
        "y_test": y_test,
        "y_pred": y_pred,
        "prediccion_por_hora": pred_df,
        "confusion_matrix": confusion_matrix(y_test, y_pred),
    }


# ---------------------------------------------------------------------------
# Ejecución directa de prueba
# ---------------------------------------------------------------------------
if __name__ == "__main__":
    from limpieza_datos import cargar_datos

    df = cargar_datos(verbose=False)

    print("\n")
    res_knn = clasificar_tipo_trafico(df, k=5)

    print("\n")
    res_cluster = clustering_equipos(df, k=3)

    print("\n")
    res_pred = predecir_protocolo_por_hora(df)
