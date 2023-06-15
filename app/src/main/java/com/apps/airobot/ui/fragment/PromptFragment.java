package com.apps.airobot.ui.fragment;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.apps.airobot.R;

import java.util.Arrays;
import java.util.Random;

public class PromptFragment extends Fragment {

    private ListView listView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_prompt, container, false);
        listView = view.findViewById(R.id.list_view);

        // 为ListView设置适配器
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, getData());
        listView.setAdapter(adapter);

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