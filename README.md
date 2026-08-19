准备好nacos与 sentinel 
Nacos
docker pull nacos/nacos-server:v2.3.2
docker run --name nacos -e MODE=standalone -p 8848:8848 -p 9848:9848 -p 9849:9849 -d nacos/nacos-server:v2.3.2
Sentinel Dashboard
docker pull bladex/sentinel-dashboard
docker run --name sentinel -p 8858:8858 -d bladex/sentinel-dashboard

想测试nacos的中心配置去nacos服务添加即可
打开：
localhost:8848/nacos
配置管理：
+
创建：
Data ID:
user-service.yaml
Group:
DEFAULT_GROUP
内容：
  name: Mark
  age: 20
  
想配置熔断限流，可以去sentinel dashboard配置

左边是对应的服务，点开去簇点链路即可。它是懒加载，需要调一下对应接口才可以看到。然后进行配置即可。
