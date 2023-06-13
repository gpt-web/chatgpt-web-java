package com.hncboy.chatgpt.front.util;

import java.util.ArrayList;
import java.util.List;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.SearchResults;
import io.milvus.param.ConnectParam;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;

public class EmbeddingUtil {

	/**
	 * 查询私有知识库
	 * 
	 * @param text
	 * @return
	 */
	public static String getEmbeddingPrompt(String text) {
		MilvusServiceClient milvusServiceClient = new MilvusServiceClient(
				ConnectParam.newBuilder().withHost("120.79.200.94").withPort(19530).build());
		try {
			List<String> input = new ArrayList<>();
			input.add(text);
			// 通embegging接口转成向量
			JSONObject data = new JSONObject();
			data.put("model", "text-embedding-ada-002");
			data.put("input", input);
			System.out.println(data.toString());
			// 构建HTTP请求
			HttpRequest request = HttpRequest.post("https://bytetop.cn/chat-api/embeddings")
					.header("Content-Type", "application/json")
					.header("Authorization", "Bearer 8DC3825E88DA8026DE87EA34AE0CD3EB").body(data.toString());

			// 发送HTTP请求
			HttpResponse response = request.execute();

			// 获取响应状态码和响应内容
			int statusCode = response.getStatus();
			String responseBody = response.body();
			// 打印响应结果
			System.out.println("Status Code: " + statusCode);
			JSONObject result = new JSONObject(responseBody);
			JSONArray resultList = result.getJSONArray("data");
			if (resultList.size() > 0) {
				StringBuilder content = new StringBuilder(
						"请参考以下CONTEXT内容进行回答，如CONTEXT内容与问题无关，请另外寻找更准确的答案进行回复。CONTEXT:");
				JSONObject item = resultList.getJSONObject(0);
				List<Float> vec = item.getBeanList("embedding", Float.class);
				List<List<Float>> floatVectors = new ArrayList<>();
				floatVectors.add(vec);
				SearchParam.Builder builder = SearchParam.newBuilder()
						// 集合名称
						.withCollectionName("kownledge")
						// 计算方式
						// 欧氏距离 (L2)
						// 内积 (IP)
						.withMetricType(MetricType.L2)
						// 返回多少条结果
						.withTopK(3)
						// 搜索的向量值
						.withVectors(floatVectors)
						// 搜索的Field
						.withVectorFieldName("vec").addOutField("input").withParams("{\"nprobe\":32}");
				R<SearchResults> search = milvusServiceClient.search(builder.build());
				SearchResultsWrapper wrapper = new SearchResultsWrapper(search.getData().getResults());
				List<SearchResultsWrapper.IDScore> scores = wrapper.getIDScore(0);
				boolean addFlag = false;
				if (scores.size() > 0) {
					for (int i = 0; i < scores.size(); i++) {
						SearchResultsWrapper.IDScore idScore = scores.get(i);
						float itemSimilarity = 100 * (1 - idScore.getScore() / 100);
						if (itemSimilarity > 99) {
							content.append(wrapper.getFieldData("input", 0).get(i) + ";");
							addFlag = true;
						}
					}
				}
				if (addFlag) {
					return content.toString();
				}
			}
		} catch (Exception e) {
			return "";
		} finally {
			milvusServiceClient.close();
		}
		return "";
	}
}
