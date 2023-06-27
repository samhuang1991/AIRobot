package com.apps.airobot.ui.fragment;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.apps.airobot.LogUtil;
import com.apps.airobot.R;
import com.apps.airobot.adapter.PromptListAdapter;
import com.apps.airobot.bus.RxBus;

import java.util.Arrays;
import java.util.Random;

public class PromptFragment extends Fragment {

    private ListView listView;
    private CardView cardView;
    private String[] prompt_list;

    @RequiresApi(api = Build.VERSION_CODES.P)
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_prompt, container, false);
        listView = view.findViewById(R.id.list_view);
        cardView = view.findViewById(R.id.card_view);
        cardView.setOutlineSpotShadowColor(ContextCompat.getColor(getContext(),R.color.theme_blue));

        prompt_list = getData();
        // 为ListView设置适配器
        PromptListAdapter adapter = new PromptListAdapter(requireContext(),Arrays.asList(prompt_list));
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                LogUtil.i("选中发送的内容："+prompt_list[i]);
                RxBus.getDefault().post(prompt_list[i]);
            }
        });

        return view;
    }

    private String[] getData() {
        // 返回列表数据
        return getRandomStrings(getResources().getStringArray(R.array.prompts_list),6);
    }

    /**
     * 可以从字符串数组中随机抽取若干字符串
     * @param strings 字符串数组
     * @param count 随机获取的字符串数
     * @return 抽取的字符串数组
     */
    private String[] getRandomStrings(String[] strings, int count) {
        if (strings == null || strings.length == 0 || count > strings.length) {
            return new String[0];
        }
        Random random = new Random();
        String[] result = new String[count];
        for (int i = 0; i < count; i++) {
            int index = random.nextInt(strings.length);
            result[i] = strings[index];
            strings[index] = strings[strings.length - 1];
            strings = Arrays.copyOfRange(strings, 0, strings.length - 1);
        }
        return result;
    }
}