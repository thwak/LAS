# LAS
Location Aware Source code differencing library.

For the details of the approach, please check this [draft](https://github.com/thwak/LAS/wiki/las.pdf).

## Simple Usage
```
mvn package
java -cp {jar file} main.LAS {old file path} {new file path}
```

## Parameters
LAS has three parameters used as thresholds in node matching.

These options can be given with -D options when running LAS.

|Name | Desc|
|-------------------|----------------------------------|
|las.dist.threshold | Distance threshold. (Default:0.5)|
|las.depth.threshold | Depth threshold. (Default:3)|
|las.sim.threshold | Similarity threshold. (Default:0.65)|
