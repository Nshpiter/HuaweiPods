import { defineConfig } from 'vitepress'

const repositoryUrl = 'https://github.com/Nshpiter/HuaweiPods'

export default defineConfig({
  lang: 'zh-CN',
  title: 'HuaweiPods',
  description: '为小米 HyperOS 适配华为耳机',
  base: '/',
  cleanUrls: true,
  srcExclude: ['DEBUG_CAPTURE_GUIDE.md'],
  lastUpdated: true,
  head: [
    ['link', { rel: 'icon', type: 'image/png', href: '/freebuds3.png' }],
    ['meta', { name: 'theme-color', content: '#5968f2' }],
    ['meta', { name: 'color-scheme', content: 'light dark' }]
  ],
  themeConfig: {
    logo: {
      src: '/freebuds3.png',
      alt: 'HuaweiPods'
    },
    nav: [
      { text: '快速开始', link: '/guide/getting-started' },
      { text: '支持状态', link: '/support/' },
      { text: 'GitHub', link: repositoryUrl }
    ],
    sidebar: [
      {
        text: 'HuaweiPods',
        items: [
          { text: '快速开始', link: '/guide/getting-started' },
          { text: '支持状态', link: '/support/' }
        ]
      }
    ],
    socialLinks: [
      { icon: 'github', link: repositoryUrl }
    ],
    editLink: {
      pattern: `${repositoryUrl}/edit/main/docs/:path`,
      text: '在 GitHub 上编辑此页'
    },
    lastUpdated: {
      text: '最后更新于',
      formatOptions: {
        dateStyle: 'medium',
        timeStyle: 'short'
      }
    },
    outline: {
      level: [2, 3],
      label: '页面导航'
    },
    docFooter: {
      prev: '上一页',
      next: '下一页'
    },
    returnToTopLabel: '返回顶部',
    sidebarMenuLabel: '目录',
    darkModeSwitchLabel: '外观',
    lightModeSwitchTitle: '切换到浅色模式',
    darkModeSwitchTitle: '切换到深色模式',
    search: {
      provider: 'local',
      options: {
        translations: {
          button: {
            buttonText: '搜索文档',
            buttonAriaLabel: '搜索文档'
          },
          modal: {
            noResultsText: '没有找到相关结果',
            resetButtonTitle: '清除查询',
            footer: {
              selectText: '选择',
              navigateText: '切换',
              closeText: '关闭'
            }
          }
        }
      }
    },
    footer: {
      message: '第三方开源项目，与华为、小米及其关联公司无隶属或合作关系。',
      copyright: 'Released under GPL-3.0 · HuaweiPods'
    }
  }
})
