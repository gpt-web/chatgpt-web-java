package com.hncboy.chatgpt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.read.builder.ExcelReaderBuilder;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.MutationResult;
import io.milvus.param.ConnectParam;
import io.milvus.param.R;
import io.milvus.param.dml.InsertParam;

public class EmbeggingTest {

	public static void main(String[] args) {
		// 从excel读取问答对
		String filePath = "d:/chat/embegging.xlsx";

		// 创建Excel读取器
		ExcelReaderBuilder readerBuilder = EasyExcel.read(filePath);

		// 获取读取的数据列表
		List<Map> dataList = readerBuilder.sheet(0).doReadSync();
		List<String> input = new ArrayList<>();
		// 遍历打印数据
		for (Map rowData : dataList) {
			input.add(rowData.get(0).toString());
		}
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
		MilvusServiceClient milvusServiceClient = new MilvusServiceClient(
				ConnectParam.newBuilder().withHost("120.79.200.94").withPort(19530).build());

		try {
			for (int i = 0; i < resultList.size(); i++) {
				JSONObject item = resultList.getJSONObject(i);
				List<Float> vec = item.getBeanList("embedding", Float.class);
				List<InsertParam.Field> fields = new ArrayList<>();
				List<List<Float>> floatVectors = new ArrayList<>();
				List<String> inputList = new ArrayList<>();
				inputList.add(input.get(i));
				floatVectors.add(vec);
				fields.add(new InsertParam.Field("input", inputList));
				fields.add(new InsertParam.Field("vec", floatVectors));
				// 将向量保存至milvus
				InsertParam insertParam = InsertParam.newBuilder().withCollectionName("kownledge").withFields(fields)
						.build();
				R<MutationResult> resultData = milvusServiceClient.insert(insertParam);
				if (resultData.getStatus() == 0) {
					System.out.println("第" + i + "行写入成功");
				} else {
					System.out.println("第" + i + "行写入失败");
					System.out.println(resultData.getException().getLocalizedMessage());
				}
			}
		} finally {
			milvusServiceClient.close();
		}
	}

}
