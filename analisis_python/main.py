"""
main.py
Ejecuta el análisis completo sin necesidad de Jupyter Notebook.
Pasos: carga → limpieza → modelos → gráficas

Detección automática de CSV:
  - Si existe ../captura_red.csv             → lo usa.
  - Si existen ../captura_red_YYYY-MM-DD.csv → los combina todos (1, 2 o 3 archivos).
  - Si no hay ningún CSV                     → muestra un error claro.
"""

from limpieza_datos import cargar_datos
from modelos import clasificar_tipo_trafico, clustering_equipos, predecir_protocolo_por_hora
from visualizacion import generar_todas

def main():
    print("\n" + "=" * 60)
    print("  ANÁLISIS DE TRÁFICO DE RED — NetworkMonitor")
    print("=" * 60)

    # 1. Carga y limpieza (detecta automáticamente los CSV disponibles)
    print("\n[1/4] Cargando y limpiando datos...")
    print("      Se busca captura_red.csv o captura_red_YYYY-MM-DD.csv\n")
    try:
        df = cargar_datos(verbose=True)
    except FileNotFoundError as e:
        print(e)
        return

    # Informar de cuántos archivos se combinaron
    if "origen_csv" in df.columns:
        archivos = df["origen_csv"].unique()
        print(f"\n[info] Archivos usados en el análisis ({len(archivos)}):")
        for a in archivos:
            n = (df["origen_csv"] == a).sum()
            print(f"       {a}  →  {n:,} registros")

    print(f"\n[info] Total de registros para análisis: {len(df):,}")

    # 2. Modelos
    print("\n[2/4] Ejecutando modelos de Data Science...\n")
    res_knn     = clasificar_tipo_trafico(df, k=5)
    res_cluster = clustering_equipos(df, k=3)
    res_pred    = predecir_protocolo_por_hora(df)

    # 3. Gráficas
    print("\n[3/4] Generando gráficas...")
    generar_todas(df, res_knn, res_cluster, res_pred)

    # 4. Resumen final
    print("[4/4] Resumen final:")
    print(f"  Registros analizados  : {len(df):,}")
    print(f"  Accuracy KNN          : {res_knn['accuracy']:.2%}")
    print(f"  Silhouette K-Means    : {res_cluster['silhouette']:.4f}")
    print(f"  Accuracy predictivo   : {res_pred['accuracy']:.2%}")
    print("\nGráficas guardadas en analisis_python/resultados/")
    print("Consulta CONCLUSIONES.md para el reporte completo.\n")

if __name__ == "__main__":
    main()
