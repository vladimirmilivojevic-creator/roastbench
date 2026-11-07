# Roast files and how the numbers are calculated

RoastBench reads two kinds of files: **CSV** and **Artisan `.alog`**. Example files are in the [`samples`](../samples)
folder. The roasts in them are made up.

## What the app does with every file

- Time is counted from **charge** (the moment the beans go into the drum). Samples before charge are skipped.
- Temperatures in Fahrenheit are converted to Celsius.
- Samples after **drop** (the cooling) are not imported. The numbers describe the roast, and the roast ends at drop.
- If the app had to guess something, it shows a **warning** in the preview.
- Limits: 5 MB per file and 20,000 samples.

## CSV files

You need a header row with a **time** column and a **bean temperature** column. The rest is optional.

```csv
# title: Colombia Huila medium
# green_weight_g: 1000
# roasted_weight_g: 855
Time (s),BT (C),ET (C),Event
0.0,200.1,250.0,Charge
2.0,188.2,248.3,
...
256.0,150.1,231.5,Dry End
```

| What | Allowed |
|---|---|
| Separator | comma, semicolon or tab |
| Time | seconds (`543`) or `m:ss` (`9:03`) |
| Bean temperature column | `BT`, `Bean`, `Bean temp` |
| Environment temperature column | `ET`, `Env`, `Drum`, `Exhaust` (optional) |
| Events column | `Event`, with words like `Charge`, `Dry End`, `FC Start`, `FC End`, `Drop` |
| Extra info | lines at the top like `# title: ...`, `# green_weight_g: ...`, `# roasted_weight_g: ...`, `# unit: F` |

If a row has a wrong value, the app tells you the line number, for example `Line 4: invalid bean temperature 'abc'`.

## Artisan `.alog` files

An `.alog` file is a text file that looks like a Python dictionary. The app reads it with its own small reader. The reader
**never runs any code** from the file. It only reads the data.

These fields are used:

| Field | Meaning |
|---|---|
| `timex` | the time of each sample, in seconds |
| `temp2` | bean temperature (BT). `-1` means "no reading" |
| `temp1` | environment temperature (ET) |
| `timeindex` | where the events are: charge, dry end, first crack start and end, second crack start and end, drop, cool |
| `weight` | `[green, roasted, unit]` |
| `mode` | `C` or `F` |
| `title`, `beans`, `roastertype` | name, coffee and machine |

## How the numbers are calculated

All times are in seconds from charge. The code is in `backend/src/main/java/dev/roastbench/analysis`.

| Number | How |
|---|---|
| **RoR** (°C per minute) | For each sample, draw the best straight line through the bean temperatures in a 30-second window around it. The slope of that line is the RoR. This is smoother than just using two samples. |
| **Total time** | from charge to drop |
| **Turning point** | the lowest bean temperature in the first 4 minutes |
| **Dry end** | taken from the file, or the first time the beans reach 150 °C |
| **Phases** | drying (charge to dry end), Maillard (dry end to first crack), development (first crack to drop) |
| **DTR** | development time ÷ total time × 100 |
| **Roast loss** | (green weight − roasted weight) ÷ green weight × 100 |
| **Comparison** | Both roasts start at charge. Every second, the app takes the difference of the two bean temperatures. It also compares dry end, first crack, drop, DTR and roast loss. |

If the data does not make sense (for example first crack before dry end, or roasted weight bigger than green weight),
the app says so and does not save the roast.
