# Formato do pack

TSV com cabeçalho opcional. Colunas, nesta ordem:

| # | Coluna | Unidade |
|---|---|---|
| 0 | name | — |
| 1 | brand | — |
| 2 | category | — |
| 3 | barcode | GTIN/EAN |
| 4 | kcal | kcal/100 g |
| 5 | protein | g/100 g |
| 6 | fat | g/100 g |
| 7 | carbs | g/100 g |
| 8 | fiber | g |
| 9 | sugar | g |
| 10 | sodium | mg |
| 11 | saturatedFat | g |
| 12 | transFat | g |
| 13 | cholesterol | mg |
| 14 | potassium | mg |
| 15 | calcium | mg |
| 16 | iron | mg |
| 17 | magnesium | mg |
| 18 | zinc | mg |
| 19 | vitaminA | mcg |
| 20 | vitaminC | mg |
| 21 | vitaminD | mcg |
| 22 | vitaminB12 | mcg |
| 23 | omega3 | g |
| 24 | servingLabel | — |
| 25 | servingGrams | g |

Campos vazios significam "desconhecido". O app trata como 0 ao somar, mas nunca afirma que o valor foi medido.

Packs legados de 12 colunas (sem barcode e micros) ainda são aceitos pelo app.
